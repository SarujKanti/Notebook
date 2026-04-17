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
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.auth.LoginActivity
import com.skd.notebook.ui.screens.ArchiveActivity
import com.skd.notebook.ui.screens.BinActivity
import com.skd.notebook.ui.screens.FoldersActivity

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnToggleLayout: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

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

        drawerLayout    = findViewById(R.id.drawerLayout)
        navigationView  = findViewById(R.id.navigationView)
        recyclerView    = findViewById(R.id.recyclerView)
        fabAdd          = findViewById(R.id.fabAdd)
        layoutEmpty     = findViewById(R.id.layoutEmpty)
        btnToggleLayout = findViewById(R.id.btnToggleLayout)
        btnMenu         = findViewById(R.id.btnMenu)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(
            onClick     = { note -> showNoteDialog(note) },
            onLongClick = { note -> showNoteActions(note) }
        )

        setupRecyclerView()
        setupSwipeToDelete()
        setupDrawer()

        viewModel.activeNotes.observe(this) { notes ->
            adapter.submitList(notes)
            layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.startRealtimeSync()

        fabAdd.setOnClickListener          { showNoteDialog(null) }
        btnToggleLayout.setOnClickListener { toggleLayout() }
        btnMenu.setOnClickListener         { drawerLayout.openDrawer(GravityCompat.START) }
    }

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) goToLogin()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ─── Drawer ──────────────────────────────────────────────────────────────

    private fun setupDrawer() {
        val header     = navigationView.getHeaderView(0)
        val tvName     = header.findViewById<TextView>(R.id.tvNavName)
        val tvEmail    = header.findViewById<TextView>(R.id.tvNavEmail)
        val tvInitial  = header.findViewById<TextView>(R.id.tvNavInitial)
        val user       = FirebaseAuth.getInstance().currentUser
        val displayName = user?.displayName?.takeIf { it.isNotEmpty() }
            ?: user?.email?.substringBefore('@') ?: "User"
        tvName.text    = displayName
        tvEmail.text   = user?.email ?: ""
        tvInitial.text = displayName.first().uppercaseChar().toString()

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.navNotes   -> { /* already here */ }
                R.id.navFolders -> startActivity(Intent(this, FoldersActivity::class.java))
                R.id.navArchive -> startActivity(Intent(this, ArchiveActivity::class.java))
                R.id.navBin     -> startActivity(Intent(this, BinActivity::class.java))
                R.id.navSignOut -> signOut()
            }
            true
        }
    }

    // ─── RecyclerView ────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        val spanCount = resources.getInteger(R.integer.grid_span_count)
        staggeredManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = staggeredManager
        recyclerView.adapter = adapter
    }

    private fun toggleLayout() {
        val spanCount = resources.getInteger(R.integer.grid_span_count)
        isGridLayout = !isGridLayout
        staggeredManager?.spanCount = if (isGridLayout) spanCount else 1
        btnToggleLayout.setImageResource(
            if (isGridLayout) R.drawable.ic_view_module else R.drawable.ic_view_list
        )
    }

    // ─── Swipe → Bin ─────────────────────────────────────────────────────────

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
                viewModel.moveToBin(note)
                Snackbar.make(recyclerView, "Moved to Bin", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") { viewModel.restoreFromBin(note) }
                    .show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
                val item       = vh.itemView
                val iconSize   = deleteIcon?.intrinsicHeight ?: 0
                val iconMargin = (item.height - iconSize) / 2
                val iconTop    = item.top + iconMargin
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
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    // ─── Note action sheet ───────────────────────────────────────────────────

    private fun showNoteActions(note: NoteEntity) {
        val items = arrayOf("Edit", "Archive", "Move to Folder", "Move to Bin")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showNoteDialog(note)
                    1 -> {
                        viewModel.archive(note)
                        Snackbar.make(recyclerView, "Note archived", Snackbar.LENGTH_SHORT)
                            .setAction("UNDO") { viewModel.unarchive(note) }
                            .show()
                    }
                    2 -> showMoveFolderDialog(note)
                    3 -> {
                        viewModel.moveToBin(note)
                        Snackbar.make(recyclerView, "Moved to Bin", Snackbar.LENGTH_LONG)
                            .setAction("UNDO") { viewModel.restoreFromBin(note) }
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showMoveFolderDialog(note: NoteEntity) {
        val folders = viewModel.folders.value.orEmpty()
        if (folders.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No folders")
                .setMessage("Create a folder first from Menu → Folders.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = folders.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Move to folder")
            .setItems(names) { _, i -> viewModel.moveToFolder(note, folders[i].id) }
            .show()
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    private fun signOut() {
        viewModel.clearLocalData()
        FirebaseAuth.getInstance().signOut()
        GoogleSignIn.getClient(this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()
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
            val fill  = if (hex.isEmpty()) Color.WHITE
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
                    val child    = container.getChildAt(i)
                    val childHex = noteColors.getOrNull(i) ?: ""
                    val nowSel   = childHex == hex
                    (child.background as? GradientDrawable)?.setStroke(
                        if (nowSel) 4 else 2, if (nowSel) Color.DKGRAY else Color.LTGRAY
                    )
                }
            }
            container.addView(v)
        }
    }
}
