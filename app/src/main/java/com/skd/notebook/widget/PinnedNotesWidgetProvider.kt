package com.skd.notebook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.skd.notebook.R
import com.skd.notebook.ui.MainActivity

/** Resizable list widget backed by [PinnedNotesRemoteViewsService]. */
class PinnedNotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_pinned_notes)

        // Unique intent per widget instance so each gets its own RemoteViewsFactory data.
        val serviceIntent = Intent(context, PinnedNotesRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("content://com.skd.notebook.widget/$appWidgetId")
        }
        views.setRemoteAdapter(R.id.widgetListView, serviceIntent)
        views.setEmptyView(R.id.widgetListView, R.id.widgetEmptyView)

        // Tapping a pinned note opens it for editing. The per-item note id is
        // merged in via RemoteViewsFactory#getViewAt's fillInIntent.
        val openNoteIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openNotePendingIntent = PendingIntent.getActivity(
            context, appWidgetId, openNoteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widgetListView, openNotePendingIntent)

        // "+" button in the header → add a new note
        val newNoteIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_NOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val newNotePendingIntent = PendingIntent.getActivity(
            context, 100_000 + appWidgetId, newNoteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetAddButton, newNotePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetListView)
    }
}
