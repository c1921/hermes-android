package com.nousresearch.hermes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.occludingHorizontalHingeBounds
import androidx.compose.material3.adaptive.occludingVerticalHingeBounds
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.window.core.layout.WindowSizeClass

internal enum class AdaptiveWorkspaceLayout {
    COMPACT,
    EXPANDED,
}

internal data class AdaptiveHingeBounds(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
)

internal data class AdaptiveWorkspaceConfiguration(
    val layout: AdaptiveWorkspaceLayout,
    val supportsSupportingPane: Boolean = false,
    val verticalHinge: AdaptiveHingeBounds? = null,
    val safeContentPadding: PaddingValues = PaddingValues(0.dp),
)

private val ExpandedNavigationWidth = 331.dp
private val MinimumFoldDetailWidth = 360.dp
private const val SupportingPaneMinimumWidthDp = 1200

internal fun adaptiveWorkspaceConfiguration(
    windowSizeClass: WindowSizeClass,
    verticalHinge: AdaptiveHingeBounds? = null,
    horizontalHinge: AdaptiveHingeBounds? = null,
): AdaptiveWorkspaceConfiguration {
    val width = windowSizeClass.minWidthDp.dp
    val height = windowSizeClass.minHeightDp.dp
    val expanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    if (verticalHinge != null && horizontalHinge != null) {
        val useLeft = verticalHinge.left >= width - verticalHinge.right
        val useTop = horizontalHinge.top >= height - horizontalHinge.bottom
        return AdaptiveWorkspaceConfiguration(
            layout = AdaptiveWorkspaceLayout.COMPACT,
            safeContentPadding = PaddingValues.Absolute(
                left = if (useLeft) 0.dp else verticalHinge.right,
                top = if (useTop) 0.dp else horizontalHinge.bottom,
                right = if (useLeft) width - verticalHinge.left else 0.dp,
                bottom = if (useTop) height - horizontalHinge.top else 0.dp,
            ),
        )
    }

    horizontalHinge?.let { hinge ->
        val topHeight = hinge.top
        val bottomHeight = height - hinge.bottom
        return AdaptiveWorkspaceConfiguration(
            layout = AdaptiveWorkspaceLayout.COMPACT,
            safeContentPadding = if (topHeight >= bottomHeight) {
                PaddingValues(bottom = height - hinge.top)
            } else {
                PaddingValues(top = hinge.bottom)
            },
        )
    }

    verticalHinge?.let { hinge ->
        val leftWidth = hinge.left
        val rightWidth = width - hinge.right
        if (expanded && leftWidth >= ExpandedNavigationWidth && rightWidth >= MinimumFoldDetailWidth) {
            return AdaptiveWorkspaceConfiguration(
                layout = AdaptiveWorkspaceLayout.EXPANDED,
                verticalHinge = hinge,
            )
        }
        return AdaptiveWorkspaceConfiguration(
            layout = AdaptiveWorkspaceLayout.COMPACT,
            safeContentPadding = if (leftWidth >= rightWidth) {
                PaddingValues.Absolute(right = width - hinge.left)
            } else {
                PaddingValues.Absolute(left = hinge.right)
            },
        )
    }

    return AdaptiveWorkspaceConfiguration(
        layout = if (expanded) AdaptiveWorkspaceLayout.EXPANDED else AdaptiveWorkspaceLayout.COMPACT,
        supportsSupportingPane = windowSizeClass.minWidthDp >= SupportingPaneMinimumWidthDp,
    )
}

@Composable
internal fun currentAdaptiveWorkspaceConfiguration(): AdaptiveWorkspaceConfiguration {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val posture = adaptiveInfo.windowPosture
    val density = LocalDensity.current
    fun List<Rect>.coveringHingeBounds(): AdaptiveHingeBounds? = if (isEmpty()) {
        null
    } else with(density) {
        AdaptiveHingeBounds(
            left = minOf { it.left }.toDp(),
            top = minOf { it.top }.toDp(),
            right = maxOf { it.right }.toDp(),
            bottom = maxOf { it.bottom }.toDp(),
        )
    }
    val verticalHinge = (
        posture.separatingVerticalHingeBounds + posture.occludingVerticalHingeBounds
    ).coveringHingeBounds()
    val horizontalHinge = (
        posture.separatingHorizontalHingeBounds + posture.occludingHorizontalHingeBounds
    ).coveringHingeBounds()
    return adaptiveWorkspaceConfiguration(
        windowSizeClass = adaptiveInfo.windowSizeClass,
        verticalHinge = verticalHinge,
        horizontalHinge = horizontalHinge,
    )
}

@Composable
internal fun <T : Any> rememberAdaptiveWorkspacePanes(
    destinations: List<T>,
    modifier: (destination: T, compact: Boolean) -> Modifier,
    content: @Composable RowScope.(destination: T, compact: Boolean) -> Unit,
): Map<T, @Composable (compact: Boolean) -> Unit> {
    val currentModifier = rememberUpdatedState(modifier)
    val currentContent = rememberUpdatedState(content)
    val stateHolder = rememberSaveableStateHolder()
    return remember(destinations, stateHolder) {
        destinations.associateWith { destination ->
            @Composable { compact: Boolean ->
                stateHolder.SaveableStateProvider(destination) {
                    Row(currentModifier.value(destination, compact)) {
                        currentContent.value.invoke(this, destination, compact)
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T : Any> AdaptiveWorkspaceShell(
    configuration: AdaptiveWorkspaceConfiguration,
    destination: T,
    destinations: List<T>,
    isListDestination: (T) -> Boolean,
    paneModifier: (destination: T, compact: Boolean) -> Modifier,
    expandedNavigation: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    supportingPaneKey: Any? = null,
    supportingPane: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.(destination: T, compact: Boolean) -> Unit,
) {
    val destinationPanes = rememberAdaptiveWorkspacePanes(
        destinations = destinations,
        modifier = paneModifier,
        content = content,
    )
    val supportStateHolder = rememberSaveableStateHolder()

    when (configuration.layout) {
        AdaptiveWorkspaceLayout.EXPANDED -> Row(modifier) {
            expandedNavigation()
            configuration.verticalHinge?.let { hinge ->
                val hingeGapAfterNavigation = hinge.right - ExpandedNavigationWidth
                if (hingeGapAfterNavigation > 0.dp) Spacer(Modifier.width(hingeGapAfterNavigation))
            }
            Box(Modifier.weight(1f)) {
                destinationPanes.getValue(destination)(false)
            }
            if (configuration.supportsSupportingPane && supportingPane != null && supportingPaneKey != null) {
                supportStateHolder.SaveableStateProvider(supportingPaneKey) {
                    supportingPane()
                }
            }
        }

        AdaptiveWorkspaceLayout.COMPACT -> AnimatedContent(
            targetState = destination,
            transitionSpec = {
                if (!isListDestination(targetState)) {
                    slideInHorizontally(tween(260)) { it / 3 } togetherWith
                        slideOutHorizontally(tween(220)) { -it / 4 }
                } else {
                    slideInHorizontally(tween(260)) { -it / 3 } togetherWith
                        slideOutHorizontally(tween(220)) { it / 4 }
                }
            },
            modifier = modifier.fillMaxSize().padding(configuration.safeContentPadding),
            label = "mobile-master-detail",
        ) { activeDestination ->
            destinationPanes.getValue(activeDestination)(true)
        }
    }
}

internal class AdaptiveFocusState {
    var hadFocus by mutableStateOf(false)
    var previousCompactLayout by mutableStateOf<Boolean?>(null)
}

@Composable
internal fun rememberAdaptiveFocusState(): AdaptiveFocusState = rememberSaveable(
    saver = listSaver(
        save = { state ->
            listOf(
                state.hadFocus,
                state.previousCompactLayout ?: false,
                state.previousCompactLayout != null,
            )
        },
        restore = { saved ->
            AdaptiveFocusState().apply {
                hadFocus = saved[0]
                previousCompactLayout = saved[1].takeIf { saved[2] }
            }
        },
    ),
) { AdaptiveFocusState() }

@Composable
internal fun Modifier.preserveFocusAcrossAdaptiveMove(
    compactLayout: Boolean,
    state: AdaptiveFocusState,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val previousCompactLayout = state.previousCompactLayout
    val layoutChanged = previousCompactLayout != null && previousCompactLayout != compactLayout
    val restoreFocus = state.hadFocus
    LaunchedEffect(compactLayout) {
        if (restoreFocus) focusRequester.requestFocus()
        state.previousCompactLayout = compactLayout
    }
    return focusRequester(focusRequester).onFocusChanged { focus ->
        if (focus.isFocused) {
            state.hadFocus = true
        } else if (!layoutChanged) {
            state.hadFocus = false
        }
    }
}
