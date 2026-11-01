package com.skd.notebook.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.NoteColors
import com.skd.notebook.util.SortOrder
import com.skd.notebook.ui.auth.LoginActivity
import com.skd.notebook.ui.screens.AboutActivity
import com.skd.notebook.ui.screens.ArchiveActivity
import com.skd.notebook.ui.screens.BinActivity
import com.skd.notebook.ui.screens.FoldersActivity
import com.skd.notebook.ui.screens.SearchActivity
import com.skd.notebook.ui.screens.SettingsActivity
import com.skd.notebook.util.appVersionLabel
import com.skd.notebook.util.keepClearOfSystemBars
import com.skd.notebook.util.peekOnScroll
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : BaseActivity() {

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
    private lateinit var tvSectionTitle: TextView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnToggleLayout: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvSort: TextView
    private lateinit var progressSync: android.widget.ProgressBar
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySub: TextView

    /** Which notes the home list shows: everything, or only pinned ones (the pill row). */
    private enum class HomeFilter { ALL, PINNED }
    private var filter = HomeFilter.ALL
    private class HomeChip(val root: View, val count: TextView)
    private val chips = mutableMapOf<String, HomeChip>()

    private var isGridLayout = true
    private var latestNotes: List<NoteEntity> = emptyList()
    private var appliedSort: com.skd.notebook.util.SortOrder? = null
    private var appliedGrid = true
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

    private val noteColors = NoteColors.PALETTE

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        drawerLayout    = findViewById(R.id.drawerLayout)
        recyclerView    = findViewById(R.id.recyclerView)
        fabAdd          = findViewById(R.id.fabAdd)
        tvSectionTitle  = findViewById(R.id.tvSectionTitle)
        layoutEmpty     = findViewById(R.id.layoutEmpty)
        btnToggleLayout = findViewById(R.id.btnToggleLayout)
        btnMenu         = findViewById(R.id.btnMenu)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        adapter   = NoteAdapter(
            onClick     = { note -> showNoteDialog(note) },
            onLongClick = { note -> showNoteActions(note) },
            onPinClick  = { note -> viewModel.togglePin(note) },
            onMoreClick = { note -> showNoteActions(note) }
        )

        progressSync = findViewById(R.id.progressSync)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySub   = findViewById(R.id.tvEmptySub)
        tvSort       = findViewById(R.id.tvSort)

        setupRecyclerView()
        setupSwipeToDelete()
        setupDrawer()
        setupHomeHeader()

        // When sync state changes (spinner on/off), recheck empty state immediately.
        // Without this, if notes list is already empty when isSyncing flips to false,
        // activeNotes.observe never re-fires and "No notes" never appears.
        viewModel.isSyncing.observe(this) { refreshEmptyState() }

        // A new or just-edited note jumps to the top of the list (sorted newest-first), so
        // scroll up to show it. NEVER do this when the list shrank (swipe-to-delete, bin,
        // archive): scrolling while an item is being removed makes the staggered grid
        // blank out until the next update.
        var firstNoteId: String? = null
        var lastCount = -1
        viewModel.activeNotes.observe(this) { unsorted ->
            latestNotes = unsorted
            appliedSort = AppSettings.sortOrder(this)
            updateChipCounts()
            val notes = visibleNotes()
            updateSectionTitle(notes.size)
            val newFirstId = notes.firstOrNull()?.id
            // "Jump to top" only makes sense when the newest note is what sits at the top.
            val noteMovedToTop = appliedSort == com.skd.notebook.util.SortOrder.NEWEST &&
                lastCount >= 0 && notes.size >= lastCount &&
                newFirstId != null && newFirstId != firstNoteId
            firstNoteId = newFirstId
            lastCount = notes.size
            adapter.submitList(notes) {
                // scrollToPosition() called right here is swallowed by the staggered grid
                // while the "move to top" item animation is still in flight, so wait for
                // that layout pass and then anchor position 0 to the very top edge.
                if (noteMovedToTop) recyclerView.post {
                    staggeredManager?.scrollToPositionWithOffset(0, 0)
                }
            }
            refreshEmptyState()
        }

        viewModel.archivedNotes.observe(this) { setChipCount(CHIP_ARCHIVED, it.size) }
        viewModel.folders.observe(this) {
            adapter.setFolders(it)
            setChipCount(CHIP_FOLDERS, it.size)
        }
        viewModel.startRealtimeSync()

        fabAdd.peekOnScroll(recyclerView) { showNoteDialog(null) }
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
        applySettingsChanges()
    }

    /** Picks up a new default sorting / view chosen in Settings while this screen was behind. */
    private fun applySettingsChanges() {
        val sort = AppSettings.sortOrder(this)
        if (appliedSort != null && sort != appliedSort) {
            appliedSort = sort
            adapter.submitList(visibleNotes())
        }
        updateSortLabel()
        val grid = AppSettings.gridView(this)
        if (grid != appliedGrid) setGridLayout(grid)
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
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_NoteBook_GlassSheet).also { it.applyGlassBackdrop() }
        val view   = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_edit_name, null)

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

    private class NavItem(val icon: Int, val title: String, val action: () -> Unit)

    private fun setupDrawer() {
        val header       = findViewById<View>(R.id.navHeader)
        val tvName       = header.findViewById<TextView>(R.id.tvNavName)
        val tvEmail      = header.findViewById<TextView>(R.id.tvNavEmail)
        val tvInitial    = header.findViewById<TextView>(R.id.tvNavInitial)
        val ivEditPencil = header.findViewById<View>(R.id.ivNavEditPencil)
        val tvStatus     = header.findViewById<TextView>(R.id.tvNavStatus)
        val footer       = findViewById<View>(R.id.navFooter)

        // The drawer is drawn under the status and navigation bars; keep its content clear of them.
        header.keepClearOfSystemBars(top = true)
        footer.keepClearOfSystemBars(top = false, bottom = true)

        findViewById<TextView>(R.id.tvNavVersion).text = "Version ${appVersionLabel()}"

        // Status chip: green check when notes sync to the cloud, a soft dot when local-only.
        val guest = isGuestMode()
        tvStatus.text = if (guest) "Local only" else "Synced to cloud"
        if (guest) {
            tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.bg_status_dot, 0, 0, 0)
            tvStatus.compoundDrawablesRelative[0]?.mutate()?.setTint(
                ContextCompat.getColor(this, R.color.colorPrimaryLight2)
            )
        } else {
            tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_status_synced, 0, 0, 0)
        }
        val user = FirebaseAuth.getInstance().currentUser

        fun applyName(name: String) {
            tvName.text    = name
            tvInitial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        }

        if (guest) {
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
            tvInitial.setOnClickListener(clickToEdit)
        }

        // Live counts in the header card.
        viewModel.activeNotes.observe(this) { header.findViewById<TextView>(R.id.tvStatNotes).text = it.size.toString() }
        viewModel.folders.observe(this) { header.findViewById<TextView>(R.id.tvStatFolders).text = it.size.toString() }
        viewModel.archivedNotes.observe(this) { header.findViewById<TextView>(R.id.tvStatArchived).text = it.size.toString() }

        buildDrawerMenu()

        // Bottom button: Logout, or Login for a guest.
        val authTitle = findViewById<TextView>(R.id.tvNavAuth)
        val authIcon  = findViewById<ImageView>(R.id.ivNavAuth)
        authTitle.text = if (guest) "Login" else "Logout"
        if (guest) {
            authIcon.setImageResource(R.drawable.ic_google)
            authIcon.imageTintList = null
        } else {
            authIcon.setImageResource(R.drawable.ic_logout)
            authIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.drawer_logout_text))
        }
        findViewById<View>(R.id.btnNavAuth).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (guest) startGoogleSignIn() else signOut()
        }
    }

    /** Notes (selected, as this is the notes screen), Folders, Archive, Bin, Settings, About. */
    private fun buildDrawerMenu() {
        val menu = findViewById<LinearLayout>(R.id.navMenu)
        menu.removeAllViews()
        val items = listOf(
            NavItem(R.drawable.ic_notes, "Notes") { /* already here */ },
            NavItem(R.drawable.ic_folder, "Folders") { startActivity(Intent(this, FoldersActivity::class.java)) },
            NavItem(R.drawable.ic_archive, "Archive") { startActivity(Intent(this, ArchiveActivity::class.java)) },
            NavItem(R.drawable.ic_delete, "Bin") { startActivity(Intent(this, BinActivity::class.java)) },
            NavItem(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },
            NavItem(R.drawable.ic_info, "About") { startActivity(Intent(this, AboutActivity::class.java)) }
        )
        val dp = resources.displayMetrics.density
        items.forEachIndexed { index, item ->
            val selected = index == 0
            val row = layoutInflater.inflate(R.layout.item_drawer_row, menu, false)
            row.findViewById<ImageView>(R.id.rowIcon).setImageResource(item.icon)
            row.findViewById<TextView>(R.id.rowTitle).text = item.title
            if (selected) {
                row.setBackgroundResource(R.drawable.bg_drawer_row_selected)
                row.findViewById<View>(R.id.rowAccent).visibility = View.VISIBLE
            }
            row.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                item.action()
            }
            menu.addView(row)

            // Hairline between the unselected rows, inset to start under the title.
            if (!selected && index < items.lastIndex) {
                menu.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).also {
                        it.marginStart = (80 * dp).toInt()
                        it.marginEnd = (32 * dp).toInt()
                    }
                    setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
                    alpha = 0.7f
                })
            }
        }
    }

    // ─── RecyclerView ────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        isGridLayout = AppSettings.gridView(this)
        appliedGrid = isGridLayout
        val spanCount = if (isGridLayout) resources.getInteger(R.integer.grid_span_count) else 1
        staggeredManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = staggeredManager
        recyclerView.adapter = adapter
    }

    private fun setGridLayout(grid: Boolean) {
        val spanCount = resources.getInteger(R.integer.grid_span_count)
        isGridLayout = grid
        appliedGrid = grid
        staggeredManager?.spanCount = if (grid) spanCount else 1
    }

    // ─── Home header: filter pills, sort, layout ─────────────────────────────

    /** Notes the list should show right now: the active notes, filtered by the chosen pill, then sorted. */
    private fun visibleNotes(): List<NoteEntity> {
        val base = if (filter == HomeFilter.PINNED) latestNotes.filter { it.isPinned } else latestNotes
        return AppSettings.sortOrder(this).apply(base)
    }

    private fun updateSectionTitle(count: Int) {
        val name = if (filter == HomeFilter.PINNED) "Pinned" else getString(R.string.my_notes)
        tvSectionTitle.text = "$name · $count"
    }

    private fun refreshEmptyState() {
        val syncing = viewModel.isSyncing.value ?: true
        val empty   = visibleNotes().isEmpty()
        progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
        layoutEmpty.visibility  = if (empty && !syncing) View.VISIBLE else View.GONE
        if (filter == HomeFilter.PINNED && latestNotes.isNotEmpty()) {
            tvEmptyTitle.text = "No pinned notes"
            tvEmptySub.text   = "Tap the paperclip on a note to pin it here"
        } else {
            tvEmptyTitle.text = "No notes yet"
            tvEmptySub.text   = "Tap the + button to create your first note"
        }
    }

    private fun setupHomeHeader() {
        val row = findViewById<LinearLayout>(R.id.chipRow)
        row.removeAllViews()
        chips.clear()

        fun addChip(key: String, icon: Int, label: String, onClick: () -> Unit) {
            val view = layoutInflater.inflate(R.layout.item_home_chip, row, false)
            view.findViewById<ImageView>(R.id.chipIcon).setImageResource(icon)
            view.findViewById<TextView>(R.id.chipLabel).text = label
            view.setOnClickListener { onClick() }
            row.addView(view)
            chips[key] = HomeChip(view, view.findViewById(R.id.chipCount))
        }
        addChip(CHIP_ALL, R.drawable.ic_stat_notes, "All Notes") { setFilter(HomeFilter.ALL) }
        addChip(CHIP_PINNED, R.drawable.ic_widget_pin, "Pinned") { setFilter(HomeFilter.PINNED) }
        // Folders and Archived are their own screens, so those pills open them.
        addChip(CHIP_FOLDERS, R.drawable.ic_stat_folder, "Folders") {
            startActivity(Intent(this, FoldersActivity::class.java))
        }
        addChip(CHIP_ARCHIVED, R.drawable.ic_stat_archive, "Archived") {
            startActivity(Intent(this, ArchiveActivity::class.java))
        }
        paintChips()

        tvSort.setOnClickListener { showSortDialog() }
        btnToggleLayout.setOnClickListener { showLayoutDialog() }
        findViewById<View>(R.id.btnProfile).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        updateSortLabel()
    }

    private fun setFilter(newFilter: HomeFilter) {
        if (filter == newFilter) return
        filter = newFilter
        paintChips()
        val notes = visibleNotes()
        updateSectionTitle(notes.size)
        adapter.submitList(notes) { recyclerView.scrollToPosition(0) }
        refreshEmptyState()
    }

    /** The pill for the current filter is filled purple; the others are translucent. */
    private fun paintChips() {
        val selected = if (filter == HomeFilter.PINNED) CHIP_PINNED else CHIP_ALL
        chips.forEach { (key, chip) ->
            chip.root.setBackgroundResource(
                if (key == selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
            )
        }
    }

    private fun setChipCount(key: String, count: Int) {
        chips[key]?.count?.text = count.toString()
    }

    private fun updateChipCounts() {
        setChipCount(CHIP_ALL, latestNotes.size)
        setChipCount(CHIP_PINNED, latestNotes.count { it.isPinned })
    }

    private fun updateSortLabel() {
        tvSort.text = "Sort: ${AppSettings.sortOrder(this).chip}"
    }

    private fun showSortDialog() {
        val orders = SortOrder.values().toList()
        val hints = mapOf(
            SortOrder.NEWEST to "Latest notes on top",
            SortOrder.OLDEST to "Oldest notes on top",
            SortOrder.ALPHABETICAL to "A to Z, by title"
        )
        showChoiceDialog(
            title = "Sort Notes",
            subtitle = "How your notes are ordered",
            icon = R.drawable.ic_sort,
            options = orders.map { ChoiceOption(it.label, hints[it], R.drawable.ic_sort) },
            selectedIndex = orders.indexOf(AppSettings.sortOrder(this))
        ) { which ->
            AppSettings.setSortOrder(this, orders[which])
            applySettingsChanges()
        }
    }

    private fun showLayoutDialog() {
        showChoiceDialog(
            title = "Layout",
            subtitle = "How your notes are laid out",
            icon = R.drawable.ic_tune,
            options = listOf(
                ChoiceOption("Grid", "Notes side by side", R.drawable.ic_view_module),
                ChoiceOption("List", "One note per row", R.drawable.ic_view_list)
            ),
            selectedIndex = if (isGridLayout) 0 else 1
        ) { which ->
            val grid = which == 0
            AppSettings.setGridView(this, grid)
            setGridLayout(grid)
        }
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
                // The list can be re-submitted mid-swipe (cloud sync), leaving the holder
                // without a valid position — indexing with NO_POSITION (-1) would crash.
                val position = viewHolder.bindingAdapterPosition
                val note = adapter.currentList.getOrNull(position)
                if (note == null) {
                    adapter.notifyDataSetChanged()
                    return
                }
                confirmMoveToBin(onCancel = { adapter.notifyItemChanged(position) }) {
                    viewModel.moveToBin(note)
                    Snackbar.make(recyclerView, "Moved to Bin", Snackbar.LENGTH_LONG)
                        .setAction("UNDO") { viewModel.restoreFromBin(note) }
                        .show()
                }
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
                confirmMoveToBin {
                    viewModel.moveToBin(note)
                    Snackbar.make(recyclerView, "Moved to Bin", Snackbar.LENGTH_LONG)
                        .setAction("UNDO") { viewModel.restoreFromBin(note) }
                        .show()
                }
            }
        ))
    }

    private fun showMoveFolderDialog(note: NoteEntity) {
        val folders = viewModel.folders.value.orEmpty()
        if (folders.isEmpty()) {
            showAppDialog(
                title = "No folders yet",
                icon = R.drawable.ic_folder,
                message = "Create a folder first from Menu → Folders, then move notes into it.",
                positiveText = "Got it",
                negativeText = null
            )
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
        showAppDialog(
            title = "You're browsing as a guest",
            icon = R.drawable.ic_account,
            message = "You can create notes, folders and files, but they'll only be stored on " +
                "this device. Log in with your Google account to back everything up and " +
                "sync it to the cloud.",
            positiveText = "Login",
            onPositive = { startGoogleSignIn() }
        )
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
        // Saving stamps the note with the current time, which moves it to the top of the list,
        // so only save when the content really changed since it was opened / last saved.
        var lastSaved = Triple(existingNote?.title?.trim() ?: "", existingNote?.description?.trim() ?: "", selectedColor)
        val save: () -> Unit = {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            val current = Triple(title, desc, selectedColor)
            if ((title.isNotEmpty() || desc.isNotEmpty()) && current != lastSaved) {
                lastSaved = current
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

private const val CHIP_ALL = "all"
private const val CHIP_PINNED = "pinned"
private const val CHIP_FOLDERS = "folders"
private const val CHIP_ARCHIVED = "archived"
