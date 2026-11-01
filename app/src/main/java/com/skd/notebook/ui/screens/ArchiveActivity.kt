package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
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

class ArchiveActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var noteEditor: NoteEditorSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    ActionItem("Move to Bin", R.drawable.ic_delete, destructive = true) { viewModel.moveToBin(note) }
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
