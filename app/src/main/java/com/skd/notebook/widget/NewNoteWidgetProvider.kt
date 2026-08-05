package com.skd.notebook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.skd.notebook.R
import com.skd.notebook.ui.MainActivity

/** 1x1 widget: tapping it opens the app straight into the "New Note" sheet. */
class NewNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_new_note)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetNewNoteRoot, pendingIntent)

            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
