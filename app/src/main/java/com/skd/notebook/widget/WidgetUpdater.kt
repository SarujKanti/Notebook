package com.skd.notebook.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.skd.notebook.R

/** Tells any placed "Pinned Notes" widgets to re-query Room and redraw their list. */
object WidgetUpdater {
    fun refreshPinnedWidget(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PinnedNotesWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetListView)
        }
    }
}
