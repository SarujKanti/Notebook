package com.skd.notebook.ui.screens

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel

class FolderNotesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_ID   = "folder_id"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var fabAdd: ExtendedFloatingActionButton

    private lateinit var folderId: String

    private val noteColors = listOf(
        "",        "#FFCDD2", "#F8BBD9", "#FFE0B2", "#FFF9C4",
        "#DCEDC8", "#B2EBF2", "#BBDEFB", "#E1BEE7", "#D7CCC8"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_folder_notes)

        folderId = intent.getStringExtra(EXTRA_FOLDER_ID) ?: run { finish(); return }
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: "Folder"

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = folderName
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        layoutEmpty  = findViewById(R.id.layoutEmpty)
        fabAdd       = findViewById(R.id.fabAdd)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        viewModel.startRealtimeSync()   // keep cloud ↔ Room in sync while inside folder

        adapter   = NoteAdapter(
            onClick     = { note -> showNoteDialog(note) },
            onLongClick = { note ->
                val items = arrayOf("Edit", "Remove from Folder", "Move to Bin")
                MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> showNoteDialog(note)
                            1 -> viewModel.moveToFolder(note, "")   // back to root
                            2 -> viewModel.moveToBin(note)
                        }
                    }
                    .show()
            }
        )

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.getFolderNotes(folderId).observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }

        fabAdd.setOnClickListener { showNoteDialog(null) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun showNoteDialog(existingNote: NoteEntity?) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle    = view.findViewById<EditText>(R.id.etTitle)
        val etDesc     = view.findViewById<EditText>(R.id.etDesc)
        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
        val btnDone    = view.findViewById<MaterialButton>(R.id.btnDone)
        val colorRow   = view.findViewById<LinearLayout>(R.id.colorPickerRow)

        var selectedColor = existingNote?.color ?: ""

        existingNote?.let {
            etTitle.setText(it.title)
            etDesc.setText(it.description)
            applyNoteColor(dialogRoot, it.color)
        }

        buildColorPicker(colorRow, selectedColor) { chosen ->
            selectedColor = chosen
            applyNoteColor(dialogRoot, chosen)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            if (title.isNotEmpty() || desc.isNotEmpty()) {
                if (existingNote == null) {
                    viewModel.addNote(title, desc, selectedColor, folderId)
                } else {
                    viewModel.updateNote(existingNote.copy(title = title, description = desc, color = selectedColor))
                }
            }
            dialog.dismiss()
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        dialog.show()
        etDesc.requestFocus()
    }

    private fun applyNoteColor(root: LinearLayout, hex: String) {
        val bg = if (hex.isEmpty()) Color.WHITE
                 else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
        root.setBackgroundColor(bg)
    }

    private fun buildColorPicker(container: LinearLayout, currentColor: String, onPick: (String) -> Unit) {
        val sizePx   = resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
        noteColors.forEach { hex ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                it.setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            val fill  = if (hex.isEmpty()) Color.WHITE
                        else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(fill)
                val sel = hex == currentColor
                setStroke(if (sel) 4 else 2, if (sel) Color.DKGRAY else Color.LTGRAY)
            }
            v.background = shape
            v.setOnClickListener {
                onPick(hex)
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val childHex = noteColors.getOrNull(i) ?: ""
                    val nowSel = childHex == hex
                    (child.background as? GradientDrawable)?.setStroke(
                        if (nowSel) 4 else 2, if (nowSel) Color.DKGRAY else Color.LTGRAY
                    )
                }
            }
            container.addView(v)
        }
    }
}
