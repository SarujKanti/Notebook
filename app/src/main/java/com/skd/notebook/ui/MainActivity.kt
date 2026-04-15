package com.skd.notebook.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnToggleLayout: ImageButton

    private var isGridLayout = true
    private var staggeredManager: StaggeredGridLayoutManager? = null

    // Pastel palette — matches colors.xml. Empty string = default white.
    private val noteColors = listOf(
        "",         // Default / white
        "#FFCDD2",  // Red
        "#F8BBD9",  // Pink
        "#FFE0B2",  // Orange
        "#FFF9C4",  // Yellow
        "#DCEDC8",  // Green
        "#B2EBF2",  // Teal
        "#BBDEFB",  // Blue
        "#E1BEE7",  // Purple
        "#D7CCC8"   // Brown
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        fabAdd = findViewById(R.id.fabAdd)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnToggleLayout = findViewById(R.id.btnToggleLayout)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        adapter = NoteAdapter(onLongClick = { note -> showNoteDialog(note) })

        setupRecyclerView()
        setupSwipeToDelete()

        viewModel.notes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncFromCloud()

        fabAdd.setOnClickListener { showNoteDialog(null) }
        btnToggleLayout.setOnClickListener { toggleLayout() }
    }

    // ─── RecyclerView ────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        staggeredManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = staggeredManager
        recyclerView.adapter = adapter
    }

    private fun toggleLayout() {
        isGridLayout = !isGridLayout
        staggeredManager?.spanCount = if (isGridLayout) 2 else 1
        btnToggleLayout.setImageResource(
            if (isGridLayout) R.drawable.ic_view_module else R.drawable.ic_view_list
        )
    }

    // ─── Swipe to delete ─────────────────────────────────────────────────────

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete)
        val swipeBg = ColorDrawable(ContextCompat.getColor(this, R.color.swipe_delete))

        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = adapter.currentList[position]
                viewModel.delete(note)

                Snackbar.make(recyclerView, R.string.note_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { viewModel.restoreNote(note) }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = viewHolder.itemView
                val iconSize = deleteIcon?.intrinsicHeight ?: 0
                val iconMargin = (item.height - iconSize) / 2
                val iconTop = item.top + iconMargin
                val iconBottom = iconTop + iconSize

                if (dX > 0) {
                    swipeBg.setBounds(item.left, item.top, item.left + dX.toInt(), item.bottom)
                    val iconLeft = item.left + iconMargin
                    deleteIcon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconBottom)
                } else if (dX < 0) {
                    swipeBg.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
                    val iconRight = item.right - iconMargin
                    deleteIcon?.setBounds(iconRight - iconSize, iconTop, iconRight, iconBottom)
                }

                swipeBg.draw(c)
                deleteIcon?.setTint(Color.WHITE)
                deleteIcon?.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    // ─── Add / Edit dialog ───────────────────────────────────────────────────

    private fun showNoteDialog(existingNote: NoteEntity?) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDesc = view.findViewById<EditText>(R.id.etDesc)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val btnDone = view.findViewById<ImageButton>(R.id.btnDone)
        val colorRow = view.findViewById<LinearLayout>(R.id.colorPickerRow)

        var selectedColor = existingNote?.color ?: ""

        // Pre-fill when editing
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
            val desc = etDesc.text.toString().trim()
            if (title.isNotEmpty() || desc.isNotEmpty()) {
                if (existingNote == null) {
                    viewModel.addNote(title, desc, selectedColor)
                } else {
                    viewModel.updateNote(
                        existingNote.copy(title = title, description = desc, color = selectedColor)
                    )
                }
            }
            dialog.dismiss()
        }

        dialog.setContentView(view)

        // Expand to full screen
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

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun applyNoteColor(root: LinearLayout, hex: String) {
        val bg = if (hex.isEmpty()) Color.WHITE
        else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
        root.setBackgroundColor(bg)
    }

    private fun buildColorPicker(
        container: LinearLayout,
        currentColor: String,
        onPick: (String) -> Unit
    ) {
        val sizePx = resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_circle_margin)

        fun makeCircle(hex: String, selected: Boolean): View {
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                it.setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            val fillColor = if (hex.isEmpty()) Color.WHITE
            else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fillColor)
                val strokeW = if (selected) 4 else 2
                val strokeC = if (selected) Color.DKGRAY else Color.LTGRAY
                setStroke(strokeW, strokeC)
            }
            v.background = shape
            return v
        }

        noteColors.forEach { hex ->
            val circle = makeCircle(hex, hex == currentColor)
            circle.setOnClickListener {
                onPick(hex)
                // Refresh all circles' stroke to reflect new selection
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val childHex = noteColors.getOrNull(i) ?: ""
                    val isNowSelected = childHex == hex
                    (child.background as? GradientDrawable)?.setStroke(
                        if (isNowSelected) 4 else 2,
                        if (isNowSelected) Color.DKGRAY else Color.LTGRAY
                    )
                }
            }
            container.addView(circle)
        }
    }
}
