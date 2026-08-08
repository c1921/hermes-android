package com.nousresearch.hermes

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.ShortcutManager
import android.net.Uri
import com.nousresearch.hermes.platform.ACTION_NEW_HERMES_CHAT
import com.nousresearch.hermes.platform.HermesEntryRequest
import com.nousresearch.hermes.platform.destinationPendingIntent
import com.nousresearch.hermes.platform.newChatIntent
import com.nousresearch.hermes.platform.newChatPendingIntent
import com.nousresearch.hermes.platform.parseHermesEntryRequest
import com.nousresearch.hermes.platform.publishPrivacySafeShortcuts
import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `valid Hermes view becomes a bounded destination request`() {
        val request = parseHermesEntryRequest(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("hermes://chats?backend=personal&profile=default&session=session-1"),
                context,
                MainActivity::class.java,
            ),
            context.packageName,
        ) as HermesEntryRequest.OpenDestination

        assertEquals(
            HermesDestinationRoute.Chats("personal", "default", "session-1"),
            request.route,
        )
        assertTrue(request.id.startsWith("destination:"))
    }

    @Test
    fun `destination request rejects non Hermes links and authority bearing extras`() {
        assertNull(
            parseHermesEntryRequest(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test"), context, MainActivity::class.java),
                context.packageName,
            ),
        )
        assertNull(
            parseHermesEntryRequest(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("hermes://chats?backend=personal&profile=default"),
                    context,
                    MainActivity::class.java,
                ).putExtra("approval_token", "must-not-authorize"),
                context.packageName,
            ),
        )
    }

    @Test
    fun `new chat requires the exact explicit component and carries no identity`() {
        val valid = parseHermesEntryRequest(newChatIntent(context), context.packageName)
        val implicit = parseHermesEntryRequest(Intent(ACTION_NEW_HERMES_CHAT), context.packageName)
        val forged = parseHermesEntryRequest(
            newChatIntent(context).putExtra("runtime_session_id", "runtime-secret"),
            context.packageName,
        )

        assertEquals(HermesEntryRequest.NewChat(), valid)
        assertNull(implicit)
        assertNull(forged)
    }

    @Test
    fun `equivalent shares receive a stable deduplication identity`() {
        val first = parseHermesEntryRequest(
            Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Draft this"),
            context.packageName,
        ) as HermesEntryRequest.ImportDraft
        val second = parseHermesEntryRequest(
            Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Draft this"),
            context.packageName,
        ) as HermesEntryRequest.ImportDraft

        assertEquals(first.id, second.id)
        assertEquals("Draft this", first.content.text)
    }

    @Test
    fun `system entry pending intents are explicit immutable and one shot`() {
        val route = HermesDestinationRoute.Chats("personal", "default", "session-1")
        val destination = shadowOf(destinationPendingIntent(context, 101, route))
        val newChat = shadowOf(newChatPendingIntent(context, 102))

        listOf(destination, newChat).forEach { pending ->
            assertTrue(pending.isActivity)
            assertTrue(pending.isImmutable)
            assertTrue(pending.flags and PendingIntent.FLAG_ONE_SHOT != 0)
            assertEquals(MainActivity::class.java.name, pending.savedIntent.component?.className)
        }
        assertEquals(
            route,
            (parseHermesEntryRequest(destination.savedIntent, context.packageName) as
                HermesEntryRequest.OpenDestination).route,
        )
        assertEquals(
            HermesEntryRequest.NewChat(),
            parseHermesEntryRequest(newChat.savedIntent, context.packageName),
        )
    }

    @Test
    fun `launcher shortcut exposes only privacy safe new chat metadata`() {
        publishPrivacySafeShortcuts(context)

        val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single()
        val shortcutIntent = requireNotNull(shortcut.intent)

        assertEquals("New chat", shortcut.shortLabel.toString())
        assertEquals("New Hermes chat", shortcut.longLabel.toString())
        assertEquals(ACTION_NEW_HERMES_CHAT, shortcutIntent.action)
        assertNull(shortcutIntent.data)
        assertTrue(shortcutIntent.extras?.keySet().orEmpty().isEmpty())
    }
}
