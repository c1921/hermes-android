package com.nousresearch.hermes

import android.app.Application
import com.nousresearch.hermes.platform.createHermesNotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HermesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createHermesNotificationChannels(this)
    }
}
