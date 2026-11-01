package com.skd.notebook.ui.screens

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.skd.notebook.ui.BaseActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.skd.notebook.R
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.ui.ActionItem
import com.skd.notebook.ui.FolderAdapter
import com.skd.notebook.ui.NoteViewModel
import com.skd.notebook.ui.showCustomColorDialog
import com.skd.notebook.ui.showActionSheet
import com.skd.notebook.ui.showAppDialog
import com.skd.notebook.util.fitTopInsetAsPadding

class FoldersActivity : BaseActivity() {

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
        "#795548", // brown
        "#F06292", // light pink
        "#8BC34A", // lime green
        "#00BCD4", // cyan
        "#3F51B5", // indigo
        "#607D8B", // blue grey
        "#FF5722"  // deep orange
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_folders)

        findViewById<View>(R.id.appBarLayout).fitTopInsetAsPadding()
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
        showActionSheet(title = folder.name, items = listOf(
            ActionItem("Edit folder", R.drawable.ic_edit) { showFolderDialog(folder) },
            ActionItem("Delete folder", R.drawable.ic_delete, destructive = true) { confirmDelete(folder) }
        ))
    }

    private fun confirmDelete(folder: FolderEntity) {
        showAppDialog(
            title = "Delete \"${folder.name}\"?",
            icon = R.drawable.ic_delete,
            message = "Notes inside this folder will be moved back to your main notes.",
            destructive = true,
            positiveText = "Delete",
            onPositive = { viewModel.deleteFolder(folder) }
        )
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

    /**
     * Preset circles, then any custom color the user added, then a "+" tile that opens the
     * custom color dialog. Re-renders on every tap so the selection ring always follows.
     */
    private fun buildColorPicker(
        container: LinearLayout,
        currentColor: String,
        onPick: (String) -> Unit
    ) {
        val sizePx   = resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
        val primary  = ContextCompat.getColor(this, R.color.colorPrimary)
        val customColors = mutableListOf<String>()
        if (currentColor.isNotEmpty() && currentColor !in folderColors) customColors += currentColor

        fun circle(fill: Int, selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(if (selected) 5 else 2,
                      if (selected) Color.BLACK else Color.parseColor("#55000000"))
        }

        fun render(selected: String) {
            container.removeAllViews()
            (folderColors + customColors).forEach { hex ->
                val fill = if (hex.isEmpty()) primary
                           else runCatching { Color.parseColor(hex) }.getOrDefault(primary)
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                        it.setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    background = circle(fill, hex == selected)
                    setOnClickListener { onPick(hex); render(hex) }
                })
            }
            container.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                    it.setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                setPadding(sizePx / 4, sizePx / 4, sizePx / 4, sizePx / 4)
                setImageResource(R.drawable.ic_add)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorPrimary))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(3, ContextCompat.getColor(context, R.color.colorPrimary))
                }
                contentDescription = "Add custom color"
                setOnClickListener {
                    showCustomColorDialog(
                        runCatching { Color.parseColor(selected) }.getOrDefault(primary)
                    ) { hex ->
                        if (hex !in folderColors && hex !in customColors) customColors += hex
                        onPick(hex)
                        render(hex)
                    }
                }
            })
        }

        render(currentColor)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
