package com.nousresearch.hermes.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VoiceMediaActionReceiver : BroadcastReceiver() {
    @Inject lateinit var player: AndroidVoicePlayer

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AndroidVoicePlayer.ACTION_PLAY -> player.resume()
            AndroidVoicePlayer.ACTION_PAUSE -> player.pause()
            AndroidVoicePlayer.ACTION_STOP -> player.stopFromMediaControl()
        }
    }
}
