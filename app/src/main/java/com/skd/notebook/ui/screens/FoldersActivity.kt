package com.skd.notebook.ui.screens

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.skd.notebook.R
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.ui.FolderAdapter
import com.skd.notebook.ui.NoteViewModel

class FoldersActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: FolderAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var fabNewFolder: FloatingActionButton

    // Folder colour palette (hex values; empty = app primary)
    private val folderColors = listOf(
        "",        // default primary purple
        "#F44336", // red
        "#FF9800", // orange
        "#FFC107", // amber
        "#4CAF50", // green
        "#009688", // teal
        "#2196F3", // blue
        "#9C27B0", // purple
        "#E91E63", // pink
        "#795548"  // brown
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_folders)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Folders"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)
        fabNewFolder = findViewById(R.id.fabNewFolder)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = FolderAdapter(
            onClick = { folder ->
                startActivity(
                    Intent(this, FolderNotesActivity::class.java)
                        .putExtra(FolderNotesActivity.EXTRA_FOLDER_ID,   folder.id)
                        .putExtra(FolderNotesActivity.EXTRA_FOLDER_NAME, folder.name)
                )
            },
            onLongClick = { folder -> showFolderActions(folder) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.folders.observe(this) { folders ->
            adapter.submitList(folders)
            layoutEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        }

        fabNewFolder.setOnClickListener { showFolderDialog(null) }
    }

    // ── Long-press action sheet ───────────────────────────────────────────────

    private fun showFolderActions(folder: FolderEntity) {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
            .setTitle(folder.name)
            .setItems(arrayOf("✏️  Edit folder", "🗑️  Delete folder")) { _, which ->
                when (which) {
                    0 -> showFolderDialog(folder)
                    1 -> confirmDelete(folder)
                }
            }
            .show()
    }

    private fun confirmDelete(folder: FolderEntity) {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
            .setTitle("Delete \"${folder.name}\"?")
            .setMessage("Notes inside this folder will be moved back to your main notes.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteFolder(folder) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Create / Edit folder bottom-sheet ────────────────────────────────────

    private fun showFolderDialog(existing: FolderEntity?) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_folder, null)

        val tvTitle   = view.findViewById<TextView>(R.id.tvFolderDialogTitle)
        val btnClose  = view.findViewById<ImageButton>(R.id.btnFolderClose)
        val etName    = view.findViewById<TextInputEditText>(R.id.etFolderName)
        val colorRow  = view.findViewById<LinearLayout>(R.id.folderColorPickerRow)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnFolderCancel)
        val btnSave   = view.findViewById<MaterialButton>(R.id.btnFolderSave)

        tvTitle.text = if (existing == null) "New Folder" else "Edit Folder"

        var selectedColor = existing?.color ?: ""
        existing?.let { etName.setText(it.name) }

        buildColorPicker(colorRow, selectedColor) { chosen -> selectedColor = chosen }

        val dismiss = { dialog.dismiss() }
        btnClose.setOnClickListener  { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                view.findViewById<com.google.android.material.textfield.TextInputLayout>(
                    R.id.tilFolderName
                ).error = "Name cannot be empty"
                return@setOnClickListener
            }
            if (existing == null) {
                viewModel.createFolder(name, selectedColor)
            } else {
                viewModel.updateFolder(existing.copy(name = name, color = selectedColor))
            }
            dismiss()
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            BottomSheetBehavior.from(it).apply {
                state          = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed  = true
            }
        }

        // Auto-focus name field
        etName.requestFocus()
        dialog.show()
    }

    // ── Colour picker ─────────────────────────────────────────────────────────

    private fun buildColorPicker(
        container: LinearLayout,
        currentColor: String,
        onPick: (String) -> Unit
    ) {
        val sizePx   = resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_circle_margin)

        folderColors.forEachIndexed { index, hex ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                it.setMargins(marginPx, marginPx, marginPx, marginPx)
            }

            val fill = if (hex.isEmpty())
                ContextCompat.getColor(this, R.color.colorPrimary)
            else
                runCatching { Color.parseColor(hex) }
                    .getOrDefault(ContextCompat.getColor(this, R.color.colorPrimary))

            fun makeShape(selected: Boolean) = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                setStroke(if (selected) 5 else 2,
                          if (selected) Color.BLACK else Color.parseColor("#55000000"))
            }

            v.background = makeShape(hex == currentColor)

            v.setOnClickListener {
                onPick(hex)
                for (i in 0 until container.childCount) {
                    val child    = container.getChildAt(i)
                    val childHex = folderColors.getOrNull(i) ?: ""
                    (child.background as? GradientDrawable)?.setStroke(
                        if (childHex == hex) 5 else 2,
                        if (childHex == hex) Color.BLACK else Color.parseColor("#55000000")
                    )
                }
            }
            container.addView(v)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
