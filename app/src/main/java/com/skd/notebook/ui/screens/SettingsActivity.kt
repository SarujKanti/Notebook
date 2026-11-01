package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.skd.notebook.R
import com.skd.notebook.ui.BaseActivity
import com.skd.notebook.util.FontPrefs
import com.skd.notebook.util.ThemePrefs
import com.skd.notebook.util.keepClearOfSystemBars

/** Theme (Light / Dark / System default) and Font Size (Small / Medium / Large). */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.appBarLayout).keepClearOfSystemBars(top = true)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupTheme()
        setupFontSize()
    }

    /** Select the saved option first, then listen, so restoring state does not trigger a change. */
    private fun setupTheme() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.toggleTheme)
        val modes = mapOf(
            R.id.btnThemeLight to ThemePrefs.LIGHT,
            R.id.btnThemeDark to ThemePrefs.DARK,
            R.id.btnThemeSystem to ThemePrefs.SYSTEM
        )
        group.check(modes.entries.first { it.value == ThemePrefs.saved(this) }.key)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // The screen recreates itself in the new theme.
            if (isChecked) modes[checkedId]?.let { ThemePrefs.set(this, it) }
        }
    }

    private fun setupFontSize() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.toggleFont)
        val sizes = mapOf(
            R.id.btnFontSmall to FontPrefs.SMALL,
            R.id.btnFontMedium to FontPrefs.MEDIUM,
            R.id.btnFontLarge to FontPrefs.LARGE
        )
        group.check(sizes.entries.first { it.value == FontPrefs.saved(this) }.key)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sizes[checkedId]?.let { FontPrefs.set(this, it) }
            recreate() // other open screens rebuild themselves when they come back (BaseActivity)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
