package com.skd.notebook.ui.screens

import com.skd.notebook.util.dimenPx
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.skd.notebook.R
import com.skd.notebook.ui.AppLock
import com.skd.notebook.ui.BaseActivity
import com.skd.notebook.ui.showAppDialog
import com.skd.notebook.ui.ChoiceOption
import com.skd.notebook.ui.askArchivePin
import com.skd.notebook.ui.changeArchivePin
import com.skd.notebook.ui.forgotArchivePin
import com.skd.notebook.ui.setNewArchivePin
import com.skd.notebook.ui.showChoiceDialog
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.ArchivePin
import com.skd.notebook.util.FontPrefs
import com.skd.notebook.util.SortOrder
import com.skd.notebook.util.ThemePrefs

/**
 * Settings: Personalization, Note preferences, Security & privacy, Support & information.
 * Rows with a value open a choice dialog; rows with a switch change immediately.
 */
class SettingsActivity : BaseActivity() {

    private class Row(
        @DrawableRes val icon: Int,
        val title: String,
        val subtitle: String? = null,
        val value: (() -> String)? = null,
        val isOn: (() -> Boolean)? = null,
        val onToggle: ((Boolean) -> Unit)? = null,
        val onClick: (() -> Unit)? = null
    )

    private class Section(@DrawableRes val icon: Int, val title: String, val rows: List<Row>)

    private var restoringState = false
    private val switches = mutableListOf<Pair<MaterialSwitch, Row>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge to edge and pad by the real bar sizes ourselves; the old "measure where the view
        // landed" trick left the status bar area white after the screen was recreated.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        applySystemBarInsets()
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        buildScreen()
    }

    /** Purple app bar grows under the status bar; the list gets room above the navigation bar. */
    private fun applySystemBarInsets() {
        val appBar = findViewById<View>(R.id.appBarLayout)
        val content = findViewById<View>(R.id.settingsContainer)
        val baseBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(0, bars.top, 0, 0)
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, baseBottom + bars.bottom)
            insets
        }
    }

    /** Rebuilds every row; called again after a value changes so the chips show the new value. */
    private fun buildScreen() {
        val container = findViewById<LinearLayout>(R.id.settingsContainer)
        container.removeAllViews()
        switches.clear()
        sections().forEach { container.addView(inflateSection(it, container)) }
    }

    private fun sections() = listOf(
        Section(R.drawable.ic_person_filled, getString(R.string.personalization), listOf(
            Row(R.drawable.ic_theme, getString(R.string.theme), getString(R.string.theme_summary),
                value = { themeLabel() }, onClick = { chooseTheme() }),
            Row(R.drawable.ic_font_size, getString(R.string.font_size), getString(R.string.font_size_summary),
                value = { FontPrefs.LABELS[FontPrefs.saved(this)] }, onClick = { chooseFontSize() })
        )),
        Section(R.drawable.ic_notes, getString(R.string.note_preferences), listOf(
            Row(R.drawable.ic_sort, getString(R.string.default_sorting), getString(R.string.default_sorting_summary),
                value = { AppSettings.sortOrder(this).chip }, onClick = { chooseSorting() }),
            Row(R.drawable.ic_view_module, getString(R.string.default_view), getString(R.string.default_view_summary),
                value = { if (AppSettings.gridView(this)) getString(R.string.grid) else getString(R.string.list) }, onClick = { chooseView() }),
            Row(R.drawable.ic_delete, getString(R.string.delete_confirmation), getString(R.string.delete_confirmation_summary),
                isOn = { AppSettings.confirmDelete(this) },
                onToggle = { AppSettings.setConfirmDelete(this, it) })
        )),
        Section(R.drawable.ic_shield, getString(R.string.security_privacy), listOf<Row?>(
            Row(R.drawable.ic_lock, getString(R.string.app_lock), getString(R.string.app_lock_summary),
                isOn = { AppSettings.appLock(this) }, onToggle = { setAppLock(it) }),
            Row(R.drawable.ic_visibility_off, getString(R.string.hide_archive_preview), getString(R.string.hide_archive_preview_summary),
                isOn = { AppSettings.hideArchivePreview(this) },
                onToggle = { setHideArchive(it) }),
            if (AppSettings.hideArchivePreview(this) && ArchivePin.isSet(this))
                Row(R.drawable.ic_lock, getString(R.string.change_archive_pin), getString(R.string.needs_current_pin),
                    onClick = { changeArchivePin { buildScreen() } })
            else null
        ).filterNotNull()),
        Section(R.drawable.ic_info, getString(R.string.support_information), listOf(
            Row(R.drawable.ic_feedback, getString(R.string.send_feedback), onClick = { sendFeedback() }),
            Row(R.drawable.ic_star, getString(R.string.rate_app), onClick = { rateApp() }),
            Row(R.drawable.ic_privacy, getString(R.string.privacy_policy), onClick = { openLink(PRIVACY_POLICY_URL) }),
            Row(R.drawable.ic_terms, getString(R.string.terms_conditions), onClick = { showNotAvailable(getString(R.string.terms_conditions)) })
        ))
    )

    // ─── Rendering ───────────────────────────────────────────────────────────

    private fun inflateSection(section: Section, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(this)
        val card = inflater.inflate(R.layout.item_setting_section, parent, false)
        card.findViewById<TextView>(R.id.sectionTitle).text = section.title

        val rows = card.findViewById<LinearLayout>(R.id.sectionRows)
        section.rows.forEachIndexed { index, row ->
            if (index > 0) rows.addView(divider())
            rows.addView(inflateRow(row, rows))
        }
        return card
    }

    private fun inflateRow(row: Row, parent: ViewGroup): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_setting_row, parent, false)
        view.findViewById<ImageView>(R.id.rowIcon).setImageResource(row.icon)
        view.findViewById<TextView>(R.id.rowTitle).text = row.title
        view.findViewById<TextView>(R.id.rowSubtitle).apply {
            if (row.subtitle == null) visibility = View.GONE else text = row.subtitle
        }

        val chip = view.findViewById<TextView>(R.id.rowValue)
        val chevron = view.findViewById<View>(R.id.rowChevron)
        val switch = view.findViewById<MaterialSwitch>(R.id.rowSwitch)

        when {
            row.isOn != null -> {
                switch.visibility = View.VISIBLE
                switches += switch to row
                bindSwitch(switch, row)
                view.setOnClickListener { switch.toggle() }
            }
            else -> {
                chevron.visibility = View.VISIBLE
                row.value?.let { chip.visibility = View.VISIBLE; chip.text = it() }
                view.setOnClickListener { row.onClick?.invoke() }
            }
        }
        return view
    }


    /** Shows the saved value first, then listens, so setting it does not count as a change. */
    private fun bindSwitch(switch: MaterialSwitch, row: Row) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = row.isOn?.invoke() ?: false
        switch.setOnCheckedChangeListener { _, checked ->
            if (!restoringState) row.onToggle?.invoke(checked)
        }
    }

    /**
     * Every switch shares one view id, so when the screen is recreated (theme or font change)
     * Android "restores" one switch's old state onto the others and that fires their listeners,
     * which would write wrong values into the saved settings. Ignore those, then re-read the
     * real values.
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        restoringState = true
        super.onRestoreInstanceState(savedInstanceState)
        restoringState = false
        switches.forEach { (switch, row) -> bindSwitch(switch, row) }
    }
    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dimenPx(R.dimen.dp_76)
            it.marginEnd = dimenPx(R.dimen.dp_16)
        }
        setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
        alpha = 0.6f
    }

    // ─── Personalization ─────────────────────────────────────────────────────

    private fun themeLabel() = when (ThemePrefs.saved(this)) {
        ThemePrefs.LIGHT -> getString(R.string.light)
        ThemePrefs.DARK -> getString(R.string.dark)
        else -> getString(R.string.system)
    }

    /** Light / Dark / System default. Changing the theme recreates this screen. */
    private fun chooseTheme() {
        val modes = intArrayOf(ThemePrefs.SYSTEM, ThemePrefs.LIGHT, ThemePrefs.DARK)
        showChoiceDialog(
            title = getString(R.string.choose_theme_title),
            subtitle = getString(R.string.choose_theme_subtitle),
            icon = R.drawable.ic_theme,
            options = listOf(
                ChoiceOption(getString(R.string.system_default), getString(R.string.system_default_hint), R.drawable.ic_choice_phone),
                ChoiceOption(getString(R.string.light), getString(R.string.light_hint), R.drawable.ic_choice_sun),
                ChoiceOption(getString(R.string.dark), getString(R.string.dark_hint), R.drawable.ic_choice_moon)
            ),
            selectedIndex = modes.indexOf(ThemePrefs.saved(this)).coerceAtLeast(0)
        ) { which -> ThemePrefs.set(this, modes[which]) }
    }

    private fun chooseFontSize() {
        showChoiceDialog(
            title = getString(R.string.font_size),
            subtitle = getString(R.string.font_size_subtitle),
            icon = R.drawable.ic_font_size,
            options = listOf(
                ChoiceOption(getString(R.string.small), getString(R.string.small_hint), R.drawable.ic_font_size),
                ChoiceOption(getString(R.string.default_label), getString(R.string.default_hint), R.drawable.ic_font_size),
                ChoiceOption(getString(R.string.large), getString(R.string.large_hint), R.drawable.ic_font_size)
            ),
            selectedIndex = FontPrefs.saved(this)
        ) { which ->
            FontPrefs.set(this, which)
            recreate() // other open screens rebuild themselves when they come back (BaseActivity)
        }
    }

    // ─── Note preferences ────────────────────────────────────────────────────

    private fun chooseSorting() {
        val orders = SortOrder.values().toList()
        val hints = mapOf(
            SortOrder.NEWEST to getString(R.string.sort_newest_hint),
            SortOrder.OLDEST to getString(R.string.sort_oldest_hint),
            SortOrder.ALPHABETICAL to getString(R.string.sort_alphabetical_hint)
        )
        showChoiceDialog(
            title = getString(R.string.default_sorting),
            subtitle = getString(R.string.sort_notes_subtitle),
            icon = R.drawable.ic_sort,
            options = orders.map { ChoiceOption(it.label, hints[it], R.drawable.ic_sort) },
            selectedIndex = orders.indexOf(AppSettings.sortOrder(this))
        ) { which ->
            AppSettings.setSortOrder(this, orders[which])
            buildScreen()
        }
    }

    private fun chooseView() {
        showChoiceDialog(
            title = getString(R.string.default_view),
            subtitle = getString(R.string.layout_subtitle),
            icon = R.drawable.ic_view_module,
            options = listOf(
                ChoiceOption(getString(R.string.grid), getString(R.string.grid_hint), R.drawable.ic_view_module),
                ChoiceOption(getString(R.string.list), getString(R.string.list_hint), R.drawable.ic_view_list)
            ),
            selectedIndex = if (AppSettings.gridView(this)) 0 else 1
        ) { which ->
            AppSettings.setGridView(this, which == 0)
            buildScreen()
        }
    }

    // ─── Security & privacy ──────────────────────────────────────────────────

    /** App Lock uses the phone's own screen lock, so it can only be on if the phone has one. */
    private fun setAppLock(on: Boolean) {
        if (on) {
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguard.isDeviceSecure) {
                AppSettings.setAppLock(this, false)
                showAppDialog(
                    title = getString(R.string.set_screen_lock_title),
                    icon = R.drawable.ic_lock,
                    message = getString(R.string.set_screen_lock_message),
                    positiveText = getString(R.string.got_it),
                    negativeText = null
                )
                buildScreen()
                return
            }
            // The user is using the phone right now, so don't ask again until the app has been away.
            AppLock.locked = false
        }
        AppSettings.setAppLock(this, on)
    }

    /**
     * Turning it on asks for a new Archive PIN (switched back off if the dialog is dismissed);
     * turning it off needs the current PIN (switched back on if it is not given).
     */
    private fun setHideArchive(on: Boolean) {
        if (on) {
            if (ArchivePin.isSet(this)) {
                AppSettings.setHideArchivePreview(this, true)
                buildScreen()
                return
            }
            setNewArchivePin(
                onCancel = { buildScreen() },
                onDone = {
                    AppSettings.setHideArchivePreview(this, true)
                    buildScreen()
                }
            )
        } else {
            askArchivePin(
                title = getString(R.string.turn_off_archive_pin),
                subtitle = getString(R.string.turn_off_archive_pin_subtitle),
                positiveText = getString(R.string.turn_off),
                onForgot = { forgotArchivePin(onDone = { buildScreen() }) },
                onCancel = { buildScreen() }
            ) {
                ArchivePin.clear(this)
                AppSettings.setHideArchivePreview(this, false)
                buildScreen()
            }
        }
    }

    // ─── Support & information ───────────────────────────────────────────────

    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showAppDialog(
                title = getString(R.string.no_email_app_title),
                icon = R.drawable.ic_feedback,
                message = getString(R.string.no_email_app_message, FEEDBACK_EMAIL),
                positiveText = getString(R.string.ok),
                negativeText = null
            )
        }
    }

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            openLink("https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            showNotAvailable(getString(R.string.this_page))
        }
    }

    private fun showNotAvailable(what: String) {
        showAppDialog(
            title = getString(R.string.not_available_title),
            icon = R.drawable.ic_info,
            message = getString(R.string.not_available_message, what),
            positiveText = getString(R.string.ok),
            negativeText = null
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private companion object {
        const val FEEDBACK_EMAIL = "14skd12@gmail.com"
        const val PRIVACY_POLICY_URL = "https://skd-note-stack.netlify.app/"
    }
}
