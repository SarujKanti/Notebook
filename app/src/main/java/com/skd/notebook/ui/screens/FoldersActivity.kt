package com.skd.notebook.ui.screens

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.skd.notebook.R
import com.skd.notebook.ui.FolderAdapter
import com.skd.notebook.ui.NoteViewModel

class FoldersActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: FolderAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var fabNewFolder: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_folders)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView  = findViewById(R.id.recyclerView)
        layoutEmpty   = findViewById(R.id.layoutEmpty)
        fabNewFolder  = findViewById(R.id.fabNewFolder)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = FolderAdapter(
            onClick = { folder ->
                startActivity(
                    Intent(this, FolderNotesActivity::class.java)
                        .putExtra(FolderNotesActivity.EXTRA_FOLDER_ID, folder.id)
                        .putExtra(FolderNotesActivity.EXTRA_FOLDER_NAME, folder.name)
                )
            },
            onLongClick = { folder ->
                AlertDialog.Builder(this)
                    .setTitle("\"${folder.name}\"")
                    .setItems(arrayOf("Delete Folder")) { _, _ ->
                        AlertDialog.Builder(this)
                            .setTitle("Delete folder?")
                            .setMessage("Notes inside will be moved back to main notes.")
                            .setPositiveButton("Delete") { _, _ ->
                                // Move folder notes back to root before deleting folder
                                viewModel.folders.value?.find { it.id == folder.id }?.let {
                                    viewModel.deleteFolder(it)
                                } ?: viewModel.deleteFolder(folder)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.folders.observe(this) { folders ->
            adapter.submitList(folders)
            layoutEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        }

        fabNewFolder.setOnClickListener { showCreateFolderDialog() }
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createFolder(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
