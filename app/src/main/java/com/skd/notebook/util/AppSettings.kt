package com.skd.notebook.util

import android.content.Context
import androidx.annotation.StringRes
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity

/** How the note lists are ordered. */
enum class SortOrder(
    val key: String,
    @StringRes private val labelRes: Int,
    @StringRes private val chipRes: Int,
    @StringRes private val hintRes: Int
) {
    NEWEST("newest", R.string.sort_newest_first, R.string.sort_newest_chip, R.string.sort_newest_hint),
    OLDEST("oldest", R.string.sort_oldest_first, R.string.sort_oldest_chip, R.string.sort_oldest_hint),
    ALPHABETICAL("alphabetical", R.string.sort_alphabetical_first, R.string.sort_alphabetical_chip, R.string.sort_alphabetical_hint),
    /** The user's own order, made by long-pressing a note and dragging it. */
    CUSTOM("custom", R.string.sort_custom_first, R.string.sort_custom_chip, R.string.sort_custom_hint);

    val label: String get() = Str(labelRes)
    val chip: String get() = Str(chipRes)
    val hint: String get() = Str(hintRes)

    /** The list in this order. Notes without a title sort by their text. */
    fun apply(notes: List<NoteEntity>): List<NoteEntity> = when (this) {
        NEWEST -> notes.sortedByDescending { it.timestamp }
        OLDEST -> notes.sortedBy { it.timestamp }
        ALPHABETICAL -> notes.sortedBy { it.title.ifBlank { it.description }.trim().lowercase() }
        CUSTOM -> notes.sortedWith(compareBy<NoteEntity> { it.sortIndex }.thenByDescending { it.timestamp })
    }
}

/** Choices made in Settings, saved on the device. */
object AppSettings {
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_SORT = "default_sort"
    private const val KEY_GRID = "default_grid_view"
    private const val KEY_CONFIRM_DELETE = "confirm_delete"
    private const val KEY_APP_LOCK = "app_lock"
    private const val KEY_HIDE_ARCHIVE = "hide_archive_preview"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sortOrder(context: Context): SortOrder {
        val key = prefs(context).getString(KEY_SORT, SortOrder.NEWEST.key)
        return SortOrder.values().firstOrNull { it.key == key } ?: SortOrder.NEWEST
    }
    fun setSortOrder(context: Context, order: SortOrder) =
        prefs(context).edit().putString(KEY_SORT, order.key).apply()

    /** True = grid, false = list. The dashboard's toggle button changes this too. */
    fun gridView(context: Context) = prefs(context).getBoolean(KEY_GRID, true)
    fun setGridView(context: Context, grid: Boolean) = prefs(context).edit().putBoolean(KEY_GRID, grid).apply()

    fun confirmDelete(context: Context) = prefs(context).getBoolean(KEY_CONFIRM_DELETE, true)
    fun setConfirmDelete(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_CONFIRM_DELETE, on).apply()

    fun appLock(context: Context) = prefs(context).getBoolean(KEY_APP_LOCK, false)
    fun setAppLock(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_APP_LOCK, on).apply()

    fun hideArchivePreview(context: Context) = prefs(context).getBoolean(KEY_HIDE_ARCHIVE, false)
    fun setHideArchivePreview(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_HIDE_ARCHIVE, on).apply()
}
