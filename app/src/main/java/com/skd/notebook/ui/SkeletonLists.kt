package com.skd.notebook.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.dimenPx
import com.skd.notebook.util.noteGridColumns

/**
 * Stands in for the notes list while it loads: the same cards, as shimmering blocks, in as many
 * columns as the real list will have (one in list view, more on a tablet) and in varied heights like
 * the real staggered grid. Put it over the list and show or hide it with its visibility.
 */
class NoteSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(context.dimenPx(R.dimen.dp_4), context.dimenPx(R.dimen.dp_4), context.dimenPx(R.dimen.dp_4), 0)
        clipChildren = true
        clipToPadding = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (childCount == 0) build()
    }

    private fun build() {
        val columns = if (AppSettings.gridView(context)) context.noteGridColumns() else 1
        val inflater = LayoutInflater.from(context)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(columns) { column ->
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            // How many text lines each card shows, in a different order for each column so the grid looks staggered.
            repeat(CARDS) { index ->
                val count = LINES[(index + column * 2) % LINES.size]
                val card = inflater.inflate(R.layout.item_note_skeleton, list, false)
                card.findViewById<View>(R.id.skLine2).visibility = if (count >= 2) VISIBLE else GONE
                card.findViewById<View>(R.id.skLine3).visibility = if (count >= 3) VISIBLE else GONE
                card.findViewById<View>(R.id.skLine4).visibility = if (count >= 4) VISIBLE else GONE
                list.addView(card)
            }
            row.addView(list, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private companion object {
        const val CARDS = 6
        val LINES = listOf(3, 2, 4, 1, 3, 2)
    }
}

/** Stands in for the folders list while it loads: a stack of folder rows as shimmering blocks. */
class FolderSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(0, context.dimenPx(R.dimen.dp_6), 0, 0)
        clipChildren = true
        clipToPadding = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (childCount == 0) {
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val inflater = LayoutInflater.from(context)
            repeat(ROWS) { list.addView(inflater.inflate(R.layout.item_folder_skeleton, list, false)) }
            addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private companion object {
        const val ROWS = 9
    }
}
