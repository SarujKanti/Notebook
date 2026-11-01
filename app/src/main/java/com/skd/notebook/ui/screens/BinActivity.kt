package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel
import com.skd.notebook.ui.showAppDialog
import com.skd.notebook.util.fitTopInsetAsPadding

/**
 * Bin. Long-press a note to start selecting; tap more notes to add or remove them, or use
 * "Select all". Selected notes can be restored or deleted forever. "Empty Bin" is in the menu.
 */
class BinActivity : AppCompatActivity() {

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

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.binNotes.observe(this) { notes ->
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
        supportActionBar?.title = if (selectionMode) "${selectedIds.size} selected" else "Bin"
        toolbar.setNavigationIcon(if (selectionMode) R.drawable.ic_close_white else R.drawable.ic_arrow_back_white)
        invalidateOptionsMenu()
    }

    // ─── Menu ────────────────────────────────────────────────────────────────

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val allSelected = binNotes.isNotEmpty() && selectedIds.size == binNotes.size
        if (selectionMode) {
            menu.add(Menu.NONE, MENU_RESTORE, 1, "Restore")
                .setIcon(R.drawable.ic_restore_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_DELETE, 2, "Delete forever")
                .setIcon(R.drawable.ic_delete_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            if (!allSelected) {
                menu.add(Menu.NONE, MENU_SELECT_ALL, 3, "Select all")
                    .setIcon(R.drawable.ic_select_all_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        } else if (binNotes.isNotEmpty()) {
            menu.add(Menu.NONE, MENU_SELECT_ALL, 1, "Select all")
                .setIcon(R.drawable.ic_select_all_white).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_EMPTY, 2, "Empty Bin").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
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
            if (notes.size == 1) "Note restored" else "${notes.size} notes restored",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteSelected() {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        val what = if (notes.size == 1) "This note" else "These ${notes.size} notes"
        showAppDialog(
            title = "Delete permanently?",
            icon = R.drawable.ic_delete,
            message = "$what will be deleted forever. This cannot be undone.",
            destructive = true,
            positiveText = "Delete",
            onPositive = {
                viewModel.deleteAllPermanently(notes)
                stopSelecting()
            }
        )
    }

    private fun confirmEmptyBin() {
        showAppDialog(
            title = "Empty Bin?",
            icon = R.drawable.ic_delete,
            message = "All notes in the Bin will be permanently deleted. This cannot be undone.",
            destructive = true,
            positiveText = "Empty Bin",
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
