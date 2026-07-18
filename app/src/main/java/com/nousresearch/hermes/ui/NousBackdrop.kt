package com.nousresearch.hermes.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nousresearch.hermes.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

private data class BackdropFrame(
    @DrawableRes val drawable: Int,
    val holdMillis: Long,
)

private val BackdropSequence = listOf(
    BackdropFrame(R.drawable.nous_field_orbit, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_orbit_neural, BRIDGE_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_neural, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_neural_portal, BRIDGE_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_portal, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_portal_orbit, BRIDGE_HOLD_MILLIS),
)

@Composable
internal fun NousBackdrop(modifier: Modifier = Modifier) {
    if (!isSystemInDarkTheme()) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        val motionScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        if (motionScale <= 0f) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(BackdropSequence[frameIndex].holdMillis)
                frameIndex = (frameIndex + 1) % BackdropSequence.size
            }
        }
    }

    AnimatedContent(
        targetState = BackdropSequence[frameIndex],
        transitionSpec = {
            fadeIn(tween(BACKDROP_CROSSFADE_MILLIS)) togetherWith fadeOut(tween(BACKDROP_CROSSFADE_MILLIS))
        },
        contentKey = BackdropFrame::drawable,
        label = "nous-backdrop",
        modifier = modifier,
    ) { frame ->
        Image(
            painter = painterResource(frame.drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(BACKDROP_OPACITY),
        )
    }
}

private const val ANCHOR_HOLD_MILLIS = 150_000L
private const val BRIDGE_HOLD_MILLIS = 12_000L
private const val BACKDROP_CROSSFADE_MILLIS = 8_000
private const val BACKDROP_OPACITY = 0.13f
