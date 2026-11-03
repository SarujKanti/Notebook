package com.skd.notebook.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.skd.notebook.ui.MainActivity

/** Keeps the home screen widgets in step with the notes and with who is signed in. */
object WidgetUpdater {

    /** Widgets show notes only for a signed-in user or a guest; after logout they stay empty. */
    fun hasSession(context: Context): Boolean =
        FirebaseAuth.getInstance().currentUser != null ||
            context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_GUEST_MODE, false)

    /** Redraws any placed "Pinned Notes" widgets: their message, and a fresh query of Room for the list. */
    fun refreshPinnedWidget(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PinnedNotesWidgetProvider::class.java))
        ids.forEach { PinnedNotesWidgetProvider.update(context, manager, it) }
    }
}
