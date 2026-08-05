package com.skd.notebook.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteDatabase
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.MainActivity
import kotlinx.coroutines.runBlocking

class PinnedNotesRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<NoteEntity> = emptyList()

    override fun onCreate() {}

    /** Called on a background thread by the widget host whenever the list needs a refresh. */
    override fun onDataSetChanged() {
        notes = try {
            runBlocking { NoteDatabase.getDatabase(context).noteDao().getPinnedNotesList() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount() = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_pinned_note_item)
        val views = RemoteViews(context.packageName, R.layout.widget_pinned_note_item)

        views.setTextViewText(R.id.itemTitle, note.title)
        views.setViewVisibility(R.id.itemTitle, if (note.title.isBlank()) View.GONE else View.VISIBLE)

        views.setTextViewText(R.id.itemDesc, note.description)
        views.setViewVisibility(R.id.itemDesc, if (note.description.isBlank()) View.GONE else View.VISIBLE)

        val accentColor = if (note.color.isNotEmpty()) {
            runCatching { Color.parseColor(note.color) }.getOrNull()
        } else null
        views.setInt(R.id.itemAccent, "setBackgroundColor", accentColor ?: DEFAULT_ACCENT)

        val fillInIntent = Intent().putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
        views.setOnClickFillInIntent(R.id.pinnedItemRoot, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = notes.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true

    companion object {
        private const val DEFAULT_ACCENT = 0xFF7B5CF0.toInt() // colorPrimaryLight
    }
}
