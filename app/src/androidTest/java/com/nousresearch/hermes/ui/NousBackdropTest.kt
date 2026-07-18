package com.nousresearch.hermes.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.nousresearch.hermes.R
import com.nousresearch.hermes.ui.theme.HermesSkin
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NousBackdropTest {
    @get:Rule
    val compose = createComposeRule()

    private val sequence = listOf(
        BackdropFrame(R.drawable.nous_field_orbit, 100L),
        BackdropFrame(R.drawable.nous_field_orbit_neural, 100L),
    )

    @Before
    fun disableAutomaticClockAdvance() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun cyclesThroughTransitionPlatesWhenMotionIsAllowed() {
        render()

        compose.onNodeWithTag(tag(sequence[0])).assertExists()
        compose.mainClock.advanceTimeBy(150L)
        compose.waitForIdle()
        compose.onNodeWithTag(tag(sequence[1])).assertExists()
    }

    @Test
    fun freezesTheCurrentPlateWhenPowerSaverStarts() {
        val powerSaveMode = mutableStateOf(false)
        render(powerSaveMode = powerSaveMode)
        compose.mainClock.advanceTimeBy(150L)
        compose.waitForIdle()
        compose.onNodeWithTag(tag(sequence[1])).assertExists()

        compose.runOnIdle { powerSaveMode.value = true }
        compose.mainClock.advanceTimeBy(250L)
        compose.waitForIdle()

        compose.onNodeWithTag(tag(sequence[1])).assertExists()
        compose.onNodeWithTag(tag(sequence[0])).assertDoesNotExist()
    }

    @Test
    fun remainsStaticWhenSystemMotionIsDisabled() {
        render(motionScale = 0f)

        compose.mainClock.advanceTimeBy(350L)
        compose.waitForIdle()

        compose.onNodeWithTag(tag(sequence[0])).assertExists()
        compose.onNodeWithTag(tag(sequence[1])).assertDoesNotExist()
    }

    @Test
    fun doesNotRenderNousArtForAnotherDesktopSkin() {
        render(skin = HermesSkin.EMBER)

        compose.onNodeWithTag(tag(sequence[0])).assertDoesNotExist()
    }

    @Test
    fun doesNotPlaceBlueFieldArtOverTheLightNousPalette() {
        render(darkTheme = false)

        compose.onNodeWithTag(tag(sequence[0])).assertDoesNotExist()
    }

    private fun render(
        skin: HermesSkin = HermesSkin.NOUS,
        darkTheme: Boolean = true,
        powerSaveMode: State<Boolean> = mutableStateOf(false),
        motionScale: Float = 1f,
    ) {
        compose.setContent {
            HermesTheme(skin) {
                NousBackdrop(
                    skin = skin,
                    darkTheme = darkTheme,
                    powerSaveMode = powerSaveMode.value,
                    motionScaleOverride = motionScale,
                    sequence = sequence,
                    crossfadeMillis = 0,
                )
            }
        }
    }

    private fun tag(frame: BackdropFrame) = "nous-backdrop-frame-${frame.drawable}"
}
