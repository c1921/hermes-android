package com.nousresearch.hermes

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nousresearch.hermes.data.PrivacyPreferences
import com.nousresearch.hermes.platform.HermesEntryRequestStore
import com.nousresearch.hermes.platform.parseHermesEntryRequest
import com.nousresearch.hermes.platform.publishPrivacySafeShortcuts
import com.nousresearch.hermes.ui.HermesApp
import com.nousresearch.hermes.ui.theme.HermesSkin
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var privacyPreferences: PrivacyPreferences
    @Inject lateinit var entryRequestStore: HermesEntryRequestStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) publishEntryRequest(intent)
        runCatching { publishPrivacySafeShortcuts(this) }
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
            val skin by privacyPreferences.skin.collectAsStateWithLifecycle(initialValue = HermesSkin.NOUS)
            val entryRequests by entryRequestStore.deliveries.collectAsStateWithLifecycle()
            HermesApp(
                secureScreen = secureScreen,
                onSecureScreenChange = { enabled ->
                    lifecycleScope.launch { privacyPreferences.setSecureScreen(enabled) }
                },
                skin = skin,
                onSkinChange = { selected ->
                    lifecycleScope.launch { privacyPreferences.setSkin(selected) }
                },
                entryDelivery = entryRequests.firstOrNull(),
                onEntryConsumed = ::consumeEntryRequest,
                onEntryFailed = entryRequestStore::fail,
                onEntryRetry = entryRequestStore::retry,
                onEntryDiscard = ::discardEntryRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishEntryRequest(intent)
    }

    private fun publishEntryRequest(intent: Intent?) {
        parseHermesEntryRequest(intent, packageName)?.let(entryRequestStore::enqueue)
        // Clear accepted and rejected external payloads alike. The process-private
        // store owns valid delivery; malformed extras must not survive task restore.
        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun consumeEntryRequest(id: String) {
        entryRequestStore.consume(id)
        clearDeliveredIntent()
    }

    private fun discardEntryRequest(id: String) {
        entryRequestStore.discard(id)
        clearDeliveredIntent()
    }

    private fun clearDeliveredIntent() {
        if (entryRequestStore.deliveries.value.isEmpty()) {
            setIntent(Intent(this, MainActivity::class.java))
        }
    }
}
