package com.nousresearch.hermes.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nousresearch.hermes.R
import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute

enum class HermesNotificationPermission { GRANTED, REQUEST, SETTINGS }

enum class HermesNotificationKind(
    internal val channelId: String,
    internal val channelName: Int,
    internal val channelDescription: Int,
    internal val title: Int,
    internal val importance: Int,
) {
    COMPLETION("hermes_completion", R.string.notification_channel_completions, R.string.notification_channel_completions_description, R.string.notification_title_completion, NotificationManager.IMPORTANCE_DEFAULT),
    ACTION_REQUIRED("hermes_action_required", R.string.notification_channel_action_required, R.string.notification_channel_action_required_description, R.string.notification_title_action_required, NotificationManager.IMPORTANCE_HIGH),
    AUTOMATION_FAILURE("hermes_automation_failure", R.string.notification_channel_automation_failures, R.string.notification_channel_automation_failures_description, R.string.notification_title_automation_failure, NotificationManager.IMPORTANCE_HIGH),
    CRON_RESULT("hermes_cron_result", R.string.notification_channel_cron_results, R.string.notification_channel_cron_results_description, R.string.notification_title_cron_result, NotificationManager.IMPORTANCE_DEFAULT),
}

fun createHermesNotificationChannels(context: Context) {
    val localizedContext = ContextCompat.getContextForLanguage(context)
    val manager = context.getSystemService(NotificationManager::class.java)
    HermesNotificationKind.entries.forEach { kind ->
        manager.createNotificationChannel(
            NotificationChannel(kind.channelId, localizedContext.getString(kind.channelName), kind.importance).apply {
                description = localizedContext.getString(kind.channelDescription)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }
}

fun hermesNotificationPermission(context: Context): HermesNotificationPermission {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return if (manager.areNotificationsEnabled()) {
            HermesNotificationPermission.GRANTED
        } else {
            HermesNotificationPermission.SETTINGS
        }
    }
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        return if (manager.areNotificationsEnabled()) {
            HermesNotificationPermission.GRANTED
        } else {
            HermesNotificationPermission.SETTINGS
        }
    }
    return if (!permissionPreferences(context).getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) {
        HermesNotificationPermission.REQUEST
    } else {
        HermesNotificationPermission.SETTINGS
    }
}

fun markHermesNotificationPermissionRequested(context: Context) {
    permissionPreferences(context).edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
}

fun postHermesNotification(
    context: Context,
    id: Int,
    kind: HermesNotificationKind,
    destination: HermesDestinationRoute,
): Boolean {
    if (hermesNotificationPermission(context) != HermesNotificationPermission.GRANTED) return false
    val localizedContext = ContextCompat.getContextForLanguage(context)
    val publicVersion = Notification.Builder(localizedContext, kind.channelId)
        .setSmallIcon(R.drawable.ic_stat_hermes)
        .setContentTitle(localizedContext.getString(R.string.app_name))
        .setContentText(localizedContext.getString(R.string.notification_open_update))
        .build()
    val notification = Notification.Builder(localizedContext, kind.channelId)
        .setSmallIcon(R.drawable.ic_stat_hermes)
        .setContentTitle(localizedContext.getString(kind.title))
        .setContentText(localizedContext.getString(R.string.notification_open_details))
        .setContentIntent(destinationPendingIntent(context, id, destination))
        .setAutoCancel(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(publicVersion)
        .build()
    return runCatching {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }.isSuccess
}

private fun permissionPreferences(context: Context) =
    context.getSharedPreferences("hermes_permissions", Context.MODE_PRIVATE)

private const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
