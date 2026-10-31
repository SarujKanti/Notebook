package com.skd.notebook.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import com.skd.notebook.R
import com.skd.notebook.util.appVersionLabel
import com.skd.notebook.util.keepClearOfSystemBars

class AboutActivity : AppCompatActivity() {

    private class Feature(@DrawableRes val icon: Int, val title: String, val description: String)

    private val features = listOf(
        Feature(R.drawable.ic_notes, "Notes in your colors",
            "Write notes with a title and text, and give each one a color so it stands out."),
        Feature(R.drawable.ic_pin, "Pin what matters",
            "Pin important notes and see them on your home screen with the Pinned Notes widget."),
        Feature(R.drawable.ic_folder, "Folders",
            "Group related notes into folders with their own colors."),
        Feature(R.drawable.ic_search, "Quick search",
            "Find any note by its title or text as you type."),
        Feature(R.drawable.ic_archive, "Archive and Bin",
            "Archive notes you want out of the way. Deleted notes go to the Bin, so you can restore them."),
        Feature(R.drawable.ic_google, "Back up with Google",
            "Sign in to sync across devices, or skip and keep everything on this device."),
        Feature(R.drawable.ic_theme, "Light and dark",
            "Follow your phone's theme, or choose light or dark yourself from the menu.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.appBarLayout).keepClearOfSystemBars(top = true)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "About"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.tvAboutVersion).text = "Version ${appVersionLabel()}"

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
