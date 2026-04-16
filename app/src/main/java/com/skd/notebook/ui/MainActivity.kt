package com.skd.notebook.ui

import android.content.Intent
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
import android.widget.PopupMenu
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
import com.google.firebase.auth.FirebaseAuth
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.auth.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnToggleLayout: ImageButton
    private lateinit var btnAccount: ImageButton

    private var isGridLayout = true
    private var staggeredManager: StaggeredGridLayoutManager? = null

    private val noteColors = listOf(
        "",        "#FFCDD2", "#F8BBD9", "#FFE0B2", "#FFF9C4",
        "#DCEDC8", "#B2EBF2", "#BBDEFB", "#E1BEE7", "#D7CCC8"
    )

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView    = findViewById(R.id.recyclerView)
        fabAdd          = findViewById(R.id.fabAdd)
        layoutEmpty     = findViewById(R.id.layoutEmpty)
        btnToggleLayout = findViewById(R.id.btnToggleLayout)
        btnAccount      = findViewById(R.id.btnAccount)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(onLongClick = { note -> showNoteDialog(note) })

        setupRecyclerView()
        setupSwipeToDelete()

        viewModel.notes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncFromCloud()

        fabAdd.setOnClickListener          { showNoteDialog(null) }
        btnToggleLayout.setOnClickListener { toggleLayout() }
        btnAccount.setOnClickListener      { showAccountMenu(it) }
    }

    override fun onStart() {
        super.onStart()
        // Guard: redirect to Login if session expired or user signed out
        if (FirebaseAuth.getInstance().currentUser == null) {
            goToLogin()
        }
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
        val swipeBg    = ColorDrawable(ContextCompat.getColor(this, R.color.swipe_delete))

        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val note = adapter.currentList[viewHolder.adapterPosition]
                viewModel.delete(note)
                Snackbar.make(recyclerView, R.string.note_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { viewModel.restoreNote(note) }
                    .show()
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
                                     viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                                     actionState: Int, isCurrentlyActive: Boolean) {
                val item      = viewHolder.itemView
                val iconSize  = deleteIcon?.intrinsicHeight ?: 0
                val iconMargin = (item.height - iconSize) / 2
                val iconTop   = item.top + iconMargin
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

    // ─── Account menu ────────────────────────────────────────────────────────

    private fun showAccountMenu(anchor: View) {
        val user = FirebaseAuth.getInstance().currentUser
        val popup = PopupMenu(this, anchor)

        // Header: show who's signed in (disabled/non-clickable)
        val label = user?.displayName?.takeIf { it.isNotEmpty() }
            ?: user?.email
            ?: user?.phoneNumber
            ?: "Account"
        popup.menu.add(0, 0, 0, "Signed in as  $label").isEnabled = false

        popup.menu.add(0, 1, 1, getString(R.string.sign_out))

        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) signOut()
            true
        }
        popup.show()
    }

    private fun signOut() {
        viewModel.clearLocalNotes()          // wipe Room before next user logs in
        FirebaseAuth.getInstance().signOut()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ─── Add / Edit note dialog ───────────────────────────────────────────────

    private fun showNoteDialog(existingNote: NoteEntity?) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle    = view.findViewById<EditText>(R.id.etTitle)
        val etDesc     = view.findViewById<EditText>(R.id.etDesc)
        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
        val btnDone    = view.findViewById<ImageButton>(R.id.btnDone)
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
                if (existingNote == null) viewModel.addNote(title, desc, selectedColor)
                else viewModel.updateNote(existingNote.copy(title = title, description = desc, color = selectedColor))
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

    // ─── Helpers ─────────────────────────────────────────────────────────────

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
            val fill = if (hex.isEmpty()) Color.WHITE
                       else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
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
