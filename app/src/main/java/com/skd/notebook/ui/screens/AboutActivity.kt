package com.skd.notebook.ui.screens

import com.skd.notebook.util.Str
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.skd.notebook.ui.BaseActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import com.skd.notebook.R
import com.skd.notebook.util.appVersionLabel
import com.skd.notebook.util.keepClearOfSystemBars

class AboutActivity : BaseActivity() {

    private class Feature(@DrawableRes val icon: Int, val title: String, val description: String)

    private val features = listOf(
        Feature(R.drawable.ic_notes, Str(R.string.feature_colors_title),
            Str(R.string.feature_colors_desc)),
        Feature(R.drawable.ic_pin, Str(R.string.feature_pin_title),
            "Pin important notes and they show up in the Pinned Notes widget on your home screen. Tap a note in the widget and that exact note opens straight away."),
        Feature(R.drawable.ic_folder, Str(R.string.folders_title),
            Str(R.string.feature_folders_desc)),
        Feature(R.drawable.ic_search, Str(R.string.feature_search_title),
            Str(R.string.feature_search_desc)),
        Feature(R.drawable.ic_delete, Str(R.string.feature_swipe_title),
            Str(R.string.feature_swipe_desc)),
        Feature(R.drawable.ic_archive, Str(R.string.feature_archive_title),
            Str(R.string.feature_archive_desc)),
        Feature(R.drawable.ic_google, Str(R.string.feature_backup_title),
            Str(R.string.feature_backup_desc)),
        Feature(R.drawable.ic_theme, Str(R.string.feature_theme_title),
            Str(R.string.feature_theme_desc))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.appBarLayout).keepClearOfSystemBars(top = true)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = Str(R.string.about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.tvAboutVersion).text = getString(R.string.version_format, appVersionLabel())

        val list = findViewById<LinearLayout>(R.id.featureList)
        features.forEach { f ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_about_feature, list, false)
            row.findViewById<ImageView>(R.id.featureIcon).setImageResource(f.icon)
            row.findViewById<TextView>(R.id.featureTitle).text = f.title
            row.findViewById<TextView>(R.id.featureDesc).text = f.description
            list.addView(row)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
