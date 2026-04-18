package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skd.notebook.R
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel

class ArchiveActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_archive)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Archive"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(
            onClick     = { /* tap to view */ },
            onLongClick = { note ->
                val items = arrayOf("Unarchive", "Move to Bin")
                AlertDialog.Builder(this)
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> viewModel.unarchive(note)
                            1 -> viewModel.moveToBin(note)
                        }
                    }
                    .show()
            }
        )

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.archivedNotes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
