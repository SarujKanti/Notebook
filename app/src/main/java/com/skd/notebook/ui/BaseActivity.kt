package com.skd.notebook.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.skd.notebook.util.FontPrefs

/**
 * Base for every screen: applies the text size chosen in Settings, and rebuilds itself when that
 * choice changed while the screen was in the background (e.g. changed in Settings, then back).
 */
open class BaseActivity : AppCompatActivity() {

    private var appliedFontSize = FontPrefs.MEDIUM

    override fun attachBaseContext(newBase: Context) {
        appliedFontSize = FontPrefs.saved(newBase)
        super.attachBaseContext(FontPrefs.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (FontPrefs.saved(this) != appliedFontSize) recreate()
    }
}
