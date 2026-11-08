package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import com.skd.notebook.ui.BaseActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.skd.notebook.R
import com.skd.notebook.util.noteGridColumns
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel
import com.skd.notebook.ui.showAppDialog
import com.skd.notebook.util.fitTopInsetAsPadding

/**
 * Bin. Long-press a note to start selecting; tap more notes to add or remove them, or use
 * "Select all". Selected notes can be restored or deleted forever. "Empty Bin" is in the menu.
 */
class BinActivity : BaseActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var toolbar: Toolbar

    private var binNotes: List<NoteEntity> = emptyList()
    private var selectionMode = false
    private val selectedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_bin)

        findViewById<View>(R.id.appBarLayout).fitTopInsetAsPadding()
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(
            onClick     = { note -> if (selectionMode) toggle(note) },
            onLongClick = { note -> if (selectionMode) toggle(note) else startSelecting(note) },
            showPin = false
        )

        val spanCount = noteGridColumns()
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        val skeleton = findViewById<View>(R.id.skeletonNotes)
        viewModel.binNotes.observe(this) { notes ->
            skeleton.visibility = View.GONE // the first list has arrived (possibly empty)
            binNotes = notes
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            // Notes can disappear underneath us (restored elsewhere, cloud sync): drop stale picks.
            selectedIds.retainAll(notes.map { it.id }.toSet())
            if (selectedIds.isEmpty()) selectionMode = false
            refreshSelectionUi()
        }

        // Back leaves selection first, and only then closes the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) stopSelecting() else finish()
            }
        })

        refreshSelectionUi()
    }

    // ─── Selection ───────────────────────────────────────────────────────────

    private fun startSelecting(first: NoteEntity) {
        selectionMode = true
        selectedIds.clear()
        selectedIds += first.id
        refreshSelectionUi()
    }

    private fun stopSelecting() {
        selectionMode = false
        selectedIds.clear()
        refreshSelectionUi()
    }

    private fun toggle(note: NoteEntity) {
        if (!selectedIds.add(note.id)) selectedIds.remove(note.id)
        if (selectedIds.isEmpty()) selectionMode = false
        refreshSelectionUi()
    }

    private fun selectAll() {
        selectionMode = binNotes.isNotEmpty()
        selectedIds.clear()
        selectedIds += binNotes.map { it.id }
        refreshSelectionUi()
    }

    private fun selectedNotes() = binNotes.filter { it.id in selectedIds }

    private fun refreshSelectionUi() {
        adapter.setSelection(selectionMode, selectedIds.toSet())
        supportActionBar?.title = if (selectionMode) getString(R.string.selected_count_format, selectedIds.size) else getString(R.string.bin_title)
        toolbar.setNavigationIcon(if (selectionMode) R.drawable.ic_close_white else R.drawable.ic_arrow_back_white)
        invalidateOptionsMenu()
    }

    // ─── Menu ────────────────────────────────────────────────────────────────

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val allSelected = binNotes.isNotEmpty() && selectedIds.size == binNotes.size
        if (selectionMode) {
            menu.add(Menu.NONE, MENU_RESTORE, 1, getString(R.string.restore))
                .setIcon(R.drawable.ic_restore_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_DELETE, 2, getString(R.string.delete_forever))
                .setIcon(R.drawable.ic_delete_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            if (!allSelected) {
                menu.add(Menu.NONE, MENU_SELECT_ALL, 3, getString(R.string.select_all))
                    .setIcon(R.drawable.ic_select_all_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        } else if (binNotes.isNotEmpty()) {
            menu.add(Menu.NONE, MENU_SELECT_ALL, 1, getString(R.string.select_all))
                .setIcon(R.drawable.ic_select_all_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_EMPTY, 2, getString(R.string.empty_bin_action)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { if (selectionMode) stopSelecting() else finish(); true }
        MENU_SELECT_ALL   -> { selectAll(); true }
        MENU_RESTORE      -> { restoreSelected(); true }
        MENU_DELETE       -> { confirmDeleteSelected(); true }
        MENU_EMPTY        -> { confirmEmptyBin(); true }
        else              -> super.onOptionsItemSelected(item)
    }

    // ─── Actions ─────────────────────────────────────────────────────────────

    private fun restoreSelected() {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        viewModel.restoreAll(notes)
        stopSelecting()
        Snackbar.make(
            recyclerView,
            if (notes.size == 1) getString(R.string.note_restored) else getString(R.string.notes_restored_format, notes.size),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteSelected() {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        val what = if (notes.size == 1) getString(R.string.this_note) else getString(R.string.these_notes_format, notes.size)
        showAppDialog(
            title = getString(R.string.delete_permanently_title),
            icon = R.drawable.ic_delete,
            message = getString(R.string.delete_permanently_message, what),
            destructive = true,
            positiveText = getString(R.string.delete),
            onPositive = {
                viewModel.deleteAllPermanently(notes)
                stopSelecting()
            }
        )
    }

    private fun confirmEmptyBin() {
        showAppDialog(
            title = getString(R.string.empty_bin_confirm_title),
            icon = R.drawable.ic_delete,
            message = getString(R.string.empty_bin_message),
            destructive = true,
            positiveText = getString(R.string.empty_bin_action),
            onPositive = { viewModel.emptyBin() }
        )
    }

    private companion object {
        const val MENU_SELECT_ALL = 1
        const val MENU_RESTORE = 2
        const val MENU_DELETE = 3
        const val MENU_EMPTY = 4
    }
}
