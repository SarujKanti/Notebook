package com.skd.notebook.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.auth.LoginActivity
import com.skd.notebook.ui.screens.ArchiveActivity
import com.skd.notebook.ui.screens.BinActivity
import com.skd.notebook.ui.screens.FoldersActivity
import com.skd.notebook.ui.screens.SearchActivity
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        /** Set on the launch intent by the "New Note" widget. */
        const val ACTION_NEW_NOTE = "com.skd.notebook.ACTION_NEW_NOTE"
        /** Note id extra set by the "Pinned Notes" widget when a list item is tapped. */
        const val EXTRA_NOTE_ID = "extra_note_id"
        /** Set by LoginActivity's Skip button so MainActivity shows the local-storage notice once. */
        const val EXTRA_SHOW_GUEST_DIALOG = "extra_show_guest_dialog"

        const val PREFS_NAME = "notebook_prefs"
        /** Whether the user chose "Skip" on the login screen and is browsing without an account. */
        const val KEY_GUEST_MODE = "guest_mode"
    }

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

    // Currently open note editor sheet (if any) — used to force a save if the
    // activity is paused/finished while it's still showing.
    private var activeNoteDialog: BottomSheetDialog? = null
    private var pendingNoteSave: (() -> Unit)? = null

    // Lets a guest sign in with Google directly from here (drawer item or the
    // guest-mode dialog) without detouring through LoginActivity.
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account    = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { onGuestSignedIn() }
                .addOnFailureListener { e ->
                    Snackbar.make(recyclerView, e.localizedMessage ?: "Sign in failed", Snackbar.LENGTH_LONG).show()
                }
        } catch (e: ApiException) {
            if (e.statusCode != 0) {   // 0 = user cancelled — silent
                Snackbar.make(recyclerView, "Sign in failed (${e.statusCode})", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** Same flow as LoginActivity's "Continue with Google" button. */
    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    /** Restart fresh as a fully-authenticated MainActivity — drawer header, sync, and menu all re-init correctly. */
    private fun onGuestSignedIn() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private val noteColors = listOf(
        "",        "#FFCDD2", "#F8BBD9", "#FFE0B2", "#FFF9C4",
        "#DCEDC8", "#B2EBF2", "#BBDEFB", "#E1BEE7", "#D7CCC8"
    )

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
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
            onLongClick = { note -> showNoteActions(note) },
            onPinClick  = { note -> viewModel.togglePin(note) }
        )

        setupRecyclerView()
        setupSwipeToDelete()
        setupDrawer()

        val progressSync = findViewById<android.widget.ProgressBar>(R.id.progressSync)

        // Helper: always recalculate visibility from current state of both LiveDatas
        fun refreshEmptyState() {
            val syncing = viewModel.isSyncing.value ?: true
            val empty   = viewModel.activeNotes.value.isNullOrEmpty()
            progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
            layoutEmpty.visibility  = if (empty && !syncing) View.VISIBLE else View.GONE
        }

        // When sync state changes (spinner on/off), recheck empty state immediately.
        // Without this, if notes list is already empty when isSyncing flips to false,
        // activeNotes.observe never re-fires and "No notes" never appears.
        viewModel.isSyncing.observe(this) { refreshEmptyState() }

        viewModel.activeNotes.observe(this) { notes ->
            adapter.submitList(notes)
            refreshEmptyState()
        }

        viewModel.startRealtimeSync()

        fabAdd.setOnClickListener          { showNoteDialog(null) }
        btnToggleLayout.setOnClickListener { toggleLayout() }
        btnMenu.setOnClickListener         { drawerLayout.openDrawer(GravityCompat.START) }

        // Search pill → open SearchActivity
        findViewById<MaterialCardView>(R.id.cardSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        handleWidgetIntent(intent)

        if (intent.getBooleanExtra(EXTRA_SHOW_GUEST_DIALOG, false)) {
            intent.removeExtra(EXTRA_SHOW_GUEST_DIALOG)
            showGuestModeDialog()
        }
    }

    private fun isGuestMode() =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_GUEST_MODE, false)

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null && !isGuestMode()) goToLogin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    // ─── Home screen widget launches ────────────────────────────────────────

    /**
     * Reached either from the "New Note" widget (opens the new-note sheet) or
     * from tapping a note inside the "Pinned Notes" widget (opens that note).
     */
    private fun handleWidgetIntent(intent: Intent) {
        if (intent.action == ACTION_NEW_NOTE) {
            showNoteDialog(null)
            intent.action = null
        }
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        if (noteId != null) {
            intent.removeExtra(EXTRA_NOTE_ID)
            lifecycleScope.launch {
                viewModel.getNoteById(noteId)?.let { showNoteDialog(it) }
            }
        }
    }

    // Reset drawer highlight to "Notes" every time we return to MainActivity
    // (e.g. after pressing back from Folders / Archive / Bin)
    override fun onResume() {
        super.onResume()
        navigationView.setCheckedItem(R.id.navNotes)
    }

    // Force-save the note editor if it's still open when the app is backgrounded,
    // the activity finishes, or the process is about to be killed — onPause is
    // guaranteed to run before any of those, unlike the dialog's dismiss callback.
    override fun onPause() {
        super.onPause()
        if (activeNoteDialog?.isShowing == true) pendingNoteSave?.invoke()
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

    // ─── SharedPreferences key for the user-edited display name ─────────────
    private val KEY_CUSTOM_NAME = "custom_display_name"

    /** Returns the name to show: custom override → Firebase displayName → email prefix */
    private fun resolvedDisplayName(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_NAME, null)
        if (!custom.isNullOrEmpty()) return custom
        val user = FirebaseAuth.getInstance().currentUser
        return user?.displayName?.takeIf { it.isNotEmpty() }
            ?: user?.email?.substringBefore('@') ?: "User"
    }

    private fun showEditNameDialog(currentName: String, onSaved: (String) -> Unit) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_edit_name, null)

        val etName    = view.findViewById<TextInputEditText>(R.id.etEditName)
        val btnClose  = view.findViewById<ImageButton>(R.id.btnEditNameClose)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnEditNameCancel)
        val btnSave   = view.findViewById<MaterialButton>(R.id.btnEditNameSave)

        etName.setText(currentName)
        etName.setSelection(etName.text?.length ?: 0)

        val dismiss = { dialog.dismiss() }
        btnClose.setOnClickListener  { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text?.toString()?.trim()?.ifEmpty { resolvedDisplayName() } ?: resolvedDisplayName()
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_NAME, newName).apply()
            onSaved(newName)
            dismiss()
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            BottomSheetBehavior.from(it).apply {
                state         = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }

        etName.requestFocus()
        dialog.show()
    }

    private fun setupDrawer() {
        val header      = navigationView.getHeaderView(0)
        val tvName      = header.findViewById<TextView>(R.id.tvNavName)
        val tvEmail     = header.findViewById<TextView>(R.id.tvNavEmail)
        val tvInitial   = header.findViewById<TextView>(R.id.tvNavInitial)
        val ivEditPencil = header.findViewById<View>(R.id.ivNavEditPencil)
        val user        = FirebaseAuth.getInstance().currentUser

        fun applyName(name: String) {
            tvName.text    = name
            tvInitial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        }

        if (isGuestMode()) {
            // Guest: no account to attach a name to, so show a fixed placeholder
            // and don't let it be edited/saved as if it were a real profile name.
            applyName("Unknown")
            tvEmail.text = ""
            ivEditPencil.visibility = View.GONE
            tvName.isClickable    = false
            tvInitial.isClickable = false
        } else {
            applyName(resolvedDisplayName())
            tvEmail.text = user?.email ?: ""
            ivEditPencil.visibility = View.VISIBLE

            // ── Tap name or avatar to edit ───────────────────────────────────
            val clickToEdit = View.OnClickListener { showEditNameDialog(tvName.text.toString(), ::applyName) }
            tvName.setOnClickListener(clickToEdit)
            header.findViewById<View>(R.id.tvNavInitial)
                .setOnClickListener(clickToEdit)
        }

        // Guest: bottom drawer item offers to log in instead of signing out.
        val authItem = navigationView.menu.findItem(R.id.navSignOut)
        if (isGuestMode()) {
            authItem.title = "Login"
            authItem.icon  = ContextCompat.getDrawable(this, R.drawable.ic_google)
        } else {
            authItem.title = "Sign Out"
            authItem.icon  = ContextCompat.getDrawable(this, R.drawable.ic_logout)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.navNotes   -> { /* already here */ }
                R.id.navFolders -> startActivity(Intent(this, FoldersActivity::class.java))
                R.id.navArchive -> startActivity(Intent(this, ArchiveActivity::class.java))
                R.id.navBin     -> startActivity(Intent(this, BinActivity::class.java))
                R.id.navSignOut -> if (isGuestMode()) startGoogleSignIn() else signOut()
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

        // Rounded to match the note card's own corner radius, instead of a flat
        // rectangle peeking out from behind the card's rounded corners.
        val swipeBg = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@MainActivity, R.color.swipe_delete))
            cornerRadius = resources.getDimension(R.dimen.card_corner_radius)
        }

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
                // Clamp to the card's own width — in a 2-column staggered grid, an
                // unclamped dX (e.g. during the fling-away animation after release)
                // grows past the item's width and spills into the next column.
                if (dX > 0) {
                    val right = (item.left + dX.toInt()).coerceAtMost(item.right)
                    swipeBg.setBounds(item.left, item.top, right, item.bottom)
                    val iconLeft = item.left + iconMargin
                    deleteIcon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconBottom)
                } else if (dX < 0) {
                    val left = (item.right + dX.toInt()).coerceAtLeast(item.left)
                    swipeBg.setBounds(left, item.top, item.right, item.bottom)
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
        showActionSheet(items = listOf(
            ActionItem("Edit", R.drawable.ic_edit) { showNoteDialog(note) },
            ActionItem("Archive", R.drawable.ic_archive) {
                viewModel.archive(note)
                Snackbar.make(recyclerView, "Note archived", Snackbar.LENGTH_SHORT)
                    .setAction("UNDO") { viewModel.unarchive(note) }
                    .show()
            },
            ActionItem("Move to Folder", R.drawable.ic_folder) { showMoveFolderDialog(note) },
            ActionItem("Move to Bin", R.drawable.ic_delete, destructive = true) {
                viewModel.moveToBin(note)
                Snackbar.make(recyclerView, "Moved to Bin", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") { viewModel.restoreFromBin(note) }
                    .show()
            }
        ))
    }

    private fun showMoveFolderDialog(note: NoteEntity) {
        val folders = viewModel.folders.value.orEmpty()
        if (folders.isEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
                .setTitle("No folders")
                .setMessage("Create a folder first from Menu → Folders.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        showActionSheet(
            title = "Move to folder",
            items = folders.map { folder ->
                ActionItem(folder.name, R.drawable.ic_folder) { viewModel.moveToFolder(note, folder.id) }
            }
        )
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    /** Shown once, right after arriving here via the login screen's Skip button. */
    private fun showGuestModeDialog() {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
            .setTitle("You're browsing as a guest")
            .setMessage(
                "You can create notes, folders and files, but they'll only be stored on " +
                "this device. Log in with your Google account to back everything up and " +
                "sync it to the cloud."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Login") { _, _ -> startGoogleSignIn() }
            .show()
    }

    private fun signOut() {
        viewModel.clearLocalData()
        FirebaseAuth.getInstance().signOut()
        GoogleSignIn.getClient(this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply()
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
        val save: () -> Unit = {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            if (title.isNotEmpty() || desc.isNotEmpty()) {
                val note = (existingNote ?: NoteEntity(id = noteId))
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
