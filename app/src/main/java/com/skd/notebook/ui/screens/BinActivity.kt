package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.Menu
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

class BinActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_bin)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Bin"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(
            onClick     = { /* read-only in bin */ },
            onLongClick = { note ->
                val items = arrayOf("Restore", "Delete Permanently")
                AlertDialog.Builder(this)
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> viewModel.restoreFromBin(note)
                            1 -> {
                                AlertDialog.Builder(this)
                                    .setTitle("Delete permanently?")
                                    .setMessage("This note will be deleted forever.")
                                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePermanently(note) }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        )

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.binNotes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, Menu.NONE, "Empty Bin")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            1 -> {
                AlertDialog.Builder(this)
                    .setTitle("Empty Bin?")
                    .setMessage("All notes in Bin will be permanently deleted.")
                    .setPositiveButton("Empty") { _, _ -> viewModel.emptyBin() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
