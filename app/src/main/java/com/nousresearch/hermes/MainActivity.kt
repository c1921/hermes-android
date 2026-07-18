package com.nousresearch.hermes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nousresearch.hermes.data.PrivacyPreferences
import com.nousresearch.hermes.platform.SharedContent
import com.nousresearch.hermes.platform.sanitizeSharedContent
import com.nousresearch.hermes.ui.HermesApp
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var privacyPreferences: PrivacyPreferences
    private val sharedContent = MutableStateFlow<List<SharedContent>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishSharedContent(intent)
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
            val pendingShares by sharedContent.collectAsStateWithLifecycle()
            HermesApp(
                secureScreen = secureScreen,
                onSecureScreenChange = { enabled ->
                    lifecycleScope.launch { privacyPreferences.setSecureScreen(enabled) }
                },
                sharedContent = pendingShares.firstOrNull(),
                onSharedContentConsumed = ::consumeSharedContent,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishSharedContent(intent)
    }

    private fun publishSharedContent(intent: Intent?) {
        val shareIntent = intent?.takeIf { it.action in SHARE_ACTIONS } ?: return
        val parsed = sanitizeSharedContent(
            id = UUID.randomUUID().toString(),
            text = shareIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: shareIntent.clipData?.let { clip ->
                    (0 until minOf(clip.itemCount, MAX_RAW_SHARE_ITEMS))
                        .firstNotNullOfOrNull { index -> clip.getItemAt(index).text?.toString() }
                },
            uriStrings = shareIntent.sharedUris().map(Uri::toString),
        ) ?: return
        if (sharedContent.value.size < MAX_PENDING_SHARES) {
            sharedContent.value = sharedContent.value + parsed
        }
    }

    private fun consumeSharedContent(id: String) {
        if (sharedContent.value.none { it.id == id }) return
        val remaining = sharedContent.value.filterNot { it.id == id }
        sharedContent.value = remaining
        if (remaining.isEmpty()) setIntent(Intent(this, MainActivity::class.java))
    }

    @Suppress("DEPRECATION")
    private fun Intent.sharedUris(): List<Uri> {
        val extras = when (action) {
            Intent.ACTION_SEND -> listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                .take(MAX_RAW_SHARE_ITEMS)
            else -> emptyList()
        }
        val clipped = clipData?.let { clip ->
            (0 until minOf(clip.itemCount, MAX_RAW_SHARE_ITEMS)).mapNotNull { index -> clip.getItemAt(index).uri }
        }.orEmpty()
        return extras + clipped
    }

    private companion object {
        val SHARE_ACTIONS = setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
        const val MAX_PENDING_SHARES = 3
        const val MAX_RAW_SHARE_ITEMS = 20
    }
}
