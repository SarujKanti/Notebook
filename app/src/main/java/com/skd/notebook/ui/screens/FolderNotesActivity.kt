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
import com.skd.notebook.ui.BaseActivity
import com.skd.notebook.ui.confirmMoveToBin
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
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.NoteColors
import com.skd.notebook.ui.ActionItem
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel
import com.skd.notebook.ui.showActionSheet
import com.skd.notebook.util.fitTopInsetAsPadding
import com.skd.notebook.util.peekOnScroll
import java.util.UUID

class FolderNotesActivity : BaseActivity() {

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

    // Currently open note editor sheet (if any) — used to force a save if the
    // activity is paused/finished while it's still showing.
    private var activeNoteDialog: BottomSheetDialog? = null
    private var pendingNoteSave: (() -> Unit)? = null

    private val noteColors = NoteColors.PALETTE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_folder_notes)

        folderId = intent.getStringExtra(EXTRA_FOLDER_ID) ?: run { finish(); return }
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: "Folder"

        findViewById<View>(R.id.appBarLayout).fitTopInsetAsPadding()
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
                showActionSheet(items = listOf(
                    ActionItem("Edit", R.drawable.ic_edit) { showNoteDialog(note) },
                    ActionItem("Remove from Folder", R.drawable.ic_folder) { viewModel.moveToFolder(note, "") },
                    ActionItem("Move to Bin", R.drawable.ic_delete, destructive = true) { confirmMoveToBin { viewModel.moveToBin(note) } }
                ))
            },
            onPinClick = { note -> viewModel.togglePin(note) }
        )

        val spanCount = if (AppSettings.gridView(this)) resources.getInteger(R.integer.grid_span_count) else 1
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        viewModel.getFolderNotes(folderId).observe(this) { notes ->
            adapter.submitList(AppSettings.sortOrder(this).apply(notes))
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }

        fabAdd.peekOnScroll(recyclerView) { showNoteDialog(null) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // Force-save the note editor if it's still open when the app is backgrounded,
    // the activity finishes, or the process is about to be killed.
    override fun onPause() {
        super.onPause()
        if (activeNoteDialog?.isShowing == true) pendingNoteSave?.invoke()
    }

    private fun showNoteDialog(existingNote: NoteEntity?) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle    = view.findViewById<EditText>(R.id.etTitle)
        val etDesc     = view.findViewById<EditText>(R.id.etDesc)
        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
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

        val noteId = existingNote?.id ?: UUID.randomUUID().toString()
        // Saving stamps the note with the current time, which moves it to the top of the list,
        // so only save when the content really changed since it was opened / last saved.
        var lastSaved = Triple(existingNote?.title?.trim() ?: "", existingNote?.description?.trim() ?: "", selectedColor)
        val save: () -> Unit = {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            val current = Triple(title, desc, selectedColor)
            if ((title.isNotEmpty() || desc.isNotEmpty()) && current != lastSaved) {
                lastSaved = current
                val note = (existingNote ?: NoteEntity(id = noteId, folderId = folderId))
                    .copy(title = title, description = desc, color = selectedColor)
                viewModel.saveNote(note)
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        // Auto-save on dismiss (back press, swipe down, tap outside, or the close arrow).
        // Also re-armed via onPause() below in case the activity is backgrounded or
        // finished while this sheet is still open — dismiss alone doesn't cover that.
        dialog.setOnDismissListener {
            save()
            activeNoteDialog = null
            pendingNoteSave = null
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        activeNoteDialog = dialog
        pendingNoteSave = save
        dialog.show()
        etDesc.requestFocus()
    }

    private fun applyNoteColor(root: LinearLayout, hex: String) {
        root.setBackgroundColor(NoteColors.background(this, hex))
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
