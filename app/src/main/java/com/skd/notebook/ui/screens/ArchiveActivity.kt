package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import com.skd.notebook.ui.BaseActivity
import com.skd.notebook.ui.confirmMoveToBin
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skd.notebook.R
import com.skd.notebook.ui.ActionItem
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteEditorSheet
import com.skd.notebook.ui.NoteViewModel
import com.skd.notebook.ui.showActionSheet
import com.skd.notebook.util.fitTopInsetAsPadding

class ArchiveActivity : BaseActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var noteEditor: NoteEditorSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide Archive Preview (Settings): blank thumbnail in recent apps and no screenshots here
        if (com.skd.notebook.util.AppSettings.hideArchivePreview(this)) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_archive)

        findViewById<View>(R.id.appBarLayout).fitTopInsetAsPadding()
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Archive"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        noteEditor = NoteEditorSheet(this, viewModel)
        adapter   = NoteAdapter(
            onClick     = { note -> noteEditor.show(note) },
            onLongClick = { note ->
                showActionSheet(items = listOf(
                    ActionItem("Unarchive", R.drawable.ic_archive) { viewModel.unarchive(note) },
                    ActionItem("Move to Bin", R.drawable.ic_delete, destructive = true) { confirmMoveToBin { viewModel.moveToBin(note) } }
                ))
            },
            showPin = false
        )

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.archivedNotes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Save the open editor if the app is backgrounded or the screen closes while it is showing.
    override fun onPause() {
        super.onPause()
        noteEditor.saveIfOpen()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
