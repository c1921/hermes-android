package com.nousresearch.hermes.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nousresearch.hermes.R
import com.nousresearch.hermes.ui.theme.HermesSkin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal data class BackdropFrame(
    @DrawableRes val drawable: Int,
    val holdMillis: Long,
)

internal val BackdropSequence = listOf(
    BackdropFrame(R.drawable.nous_field_orbit, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_orbit_neural, BRIDGE_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_neural, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_neural_portal, BRIDGE_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_portal, ANCHOR_HOLD_MILLIS),
    BackdropFrame(R.drawable.nous_field_portal_orbit, BRIDGE_HOLD_MILLIS),
)

@Composable
internal fun NousBackdrop(
    skin: HermesSkin,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    powerSaveMode: Boolean? = null,
    motionScaleOverride: Float? = null,
    sequence: List<BackdropFrame> = BackdropSequence,
    crossfadeMillis: Int = BACKDROP_CROSSFADE_MILLIS,
) {
    if (skin != HermesSkin.NOUS || !darkTheme || sequence.isEmpty()) return
    val savingPower = powerSaveMode ?: rememberPowerSaveMode()
    val lifecycleOwner = LocalLifecycleOwner.current
    var frameIndex by rememberSaveable(sequence.size) { mutableIntStateOf(0) }
    val activeFrameIndex = frameIndex.coerceIn(sequence.indices)

    LaunchedEffect(lifecycleOwner, savingPower, motionScaleOverride, sequence) {
        if (savingPower) return@LaunchedEffect
        frameIndex = frameIndex.coerceIn(sequence.indices)
        val motionScale = currentCoroutineContext()[MotionDurationScale]
        fun motionAllowed(): Boolean = (motionScaleOverride ?: motionScale?.scaleFactor ?: 1f) > 0f
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                snapshotFlow(::motionAllowed).first { it }
                delay(sequence[frameIndex].holdMillis)
                if (motionAllowed()) frameIndex = (frameIndex + 1) % sequence.size
            }
        }
    }

    if (savingPower || motionScaleOverride?.let { it <= 0f } == true) {
        BackdropImage(sequence[activeFrameIndex], modifier)
        return
    }

    AnimatedContent(
        targetState = sequence[activeFrameIndex],
        transitionSpec = {
            fadeIn(tween(crossfadeMillis)) togetherWith fadeOut(tween(crossfadeMillis))
        },
        contentKey = BackdropFrame::drawable,
        label = "nous-backdrop",
        modifier = modifier,
    ) { frame ->
        BackdropImage(frame, Modifier.fillMaxSize())
    }
}

@Composable
private fun BackdropImage(frame: BackdropFrame, modifier: Modifier) {
    Image(
        painter = painterResource(frame.drawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .testTag("nous-backdrop-frame-${frame.drawable}")
            .alpha(BACKDROP_OPACITY),
    )
}

@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current.applicationContext
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSaveMode by remember(powerManager) { mutableStateOf(powerManager.isPowerSaveMode) }

    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSaveMode = powerManager.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return powerSaveMode
}

private const val ANCHOR_HOLD_MILLIS = 150_000L
private const val BRIDGE_HOLD_MILLIS = 12_000L
private const val BACKDROP_CROSSFADE_MILLIS = 8_000
private const val BACKDROP_OPACITY = 0.13f
