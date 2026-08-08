package com.nousresearch.hermes.audio

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMediaControlsTest {
    @Test
    fun `media controls exactly follow playback state`() {
        assertEquals(
            VoiceMediaControls(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP, AndroidVoicePlayer.ACTION_PAUSE),
            voiceMediaControls(PlaybackState.STATE_PLAYING),
        )
        assertEquals(
            VoiceMediaControls(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP, AndroidVoicePlayer.ACTION_PLAY),
            voiceMediaControls(PlaybackState.STATE_PAUSED),
        )
        assertEquals(
            VoiceMediaControls(PlaybackState.ACTION_STOP, null),
            voiceMediaControls(PlaybackState.STATE_BUFFERING),
        )
    }
}
