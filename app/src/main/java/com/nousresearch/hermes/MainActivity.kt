package com.nousresearch.hermes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nousresearch.hermes.data.PrivacyPreferences
import com.nousresearch.hermes.ui.HermesApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var privacyPreferences: PrivacyPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            privacyPreferences.secureScreen.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val secureScreen by privacyPreferences.secureScreen.collectAsStateWithLifecycle(initialValue = false)
            HermesApp(
                secureScreen = secureScreen,
                onSecureScreenChange = { enabled ->
                    lifecycleScope.launch { privacyPreferences.setSecureScreen(enabled) }
                },
            )
        }
    }
}
