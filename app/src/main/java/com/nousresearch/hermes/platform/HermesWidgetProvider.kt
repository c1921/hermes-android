package com.nousresearch.hermes.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.nousresearch.hermes.R

class HermesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_new_chat).apply {
                setOnClickPendingIntent(
                    R.id.widget_new_chat,
                    newChatWidgetPendingIntent(context, appWidgetId),
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
