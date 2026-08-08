package com.nousresearch.hermes.platform

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import com.nousresearch.hermes.MainActivity
import com.nousresearch.hermes.R
import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import com.nousresearch.hermes.ui.navigation.HermesDestinationUri

const val ACTION_NEW_HERMES_CHAT = "com.nousresearch.hermes.action.NEW_CHAT"

fun parseHermesEntryRequest(
    intent: Intent?,
    expectedPackageName: String,
    expectedActivityName: String = MainActivity::class.java.name,
): HermesEntryRequest? = runCatching {
    val candidate = intent ?: return null
    when (candidate.action) {
        Intent.ACTION_VIEW -> parseDestinationRequest(candidate)
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> parseShareRequest(candidate)
        ACTION_NEW_HERMES_CHAT -> if (
            candidate.isExplicitFor(expectedPackageName, expectedActivityName) &&
            candidate.data == null &&
            candidate.clipData == null &&
            candidate.extras?.keySet().orEmpty().isEmpty()
        ) {
            HermesEntryRequest.NewChat()
        } else {
            null
        }
        else -> null
    }
}.getOrNull()

fun newChatIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    action = ACTION_NEW_HERMES_CHAT
}

fun newChatPendingIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    newChatIntent(context),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
)

fun destinationPendingIntent(
    context: Context,
    requestCode: Int,
    route: HermesDestinationRoute,
): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(HermesDestinationUri.encode(route))
    },
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
)

fun publishPrivacySafeShortcuts(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    val shortcut = ShortcutInfo.Builder(context, NEW_CHAT_SHORTCUT_ID)
        .setShortLabel("New chat")
        .setLongLabel("New Hermes chat")
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(newChatIntent(context))
        .build()
    shortcutManager.dynamicShortcuts = listOf(shortcut)
}

private fun parseDestinationRequest(intent: Intent): HermesEntryRequest.OpenDestination? {
    if (intent.clipData != null || !intent.extras?.keySet().orEmpty().isEmpty()) return null
    val route = intent.dataString?.let(HermesDestinationUri::parse) ?: return null
    return HermesEntryRequest.OpenDestination(
        id = stableEntryId("destination", HermesDestinationUri.encode(route)),
        route = route,
    )
}

private fun parseShareRequest(intent: Intent): HermesEntryRequest.ImportDraft? {
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: intent.clipData?.let { clip ->
            (0 until minOf(clip.itemCount, MAX_RAW_SHARE_ITEMS))
                .firstNotNullOfOrNull { index -> clip.getItemAt(index).text?.toString() }
        }
    val uriStrings = intent.sharedUris().map(Uri::toString)
    val provisional = sanitizeSharedContent("share", text, uriStrings) ?: return null
    val id = stableEntryId("share", provisional.text, *provisional.uriStrings.toTypedArray())
    return HermesEntryRequest.ImportDraft(id, provisional.copy(id = id))
}

private fun Intent.isExplicitFor(packageName: String, activityName: String): Boolean =
    component == ComponentName(packageName, activityName) && `package` in setOf(null, packageName)

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

private const val NEW_CHAT_SHORTCUT_ID = "new-hermes-chat"
private const val MAX_RAW_SHARE_ITEMS = 20
