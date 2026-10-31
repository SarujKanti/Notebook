package com.skd.notebook.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Light / Dark / System theme choice, saved on the device. Defaults to following the system. */
object ThemePrefs {
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    const val SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val LIGHT  = AppCompatDelegate.MODE_NIGHT_NO
    const val DARK   = AppCompatDelegate.MODE_NIGHT_YES

    fun saved(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_MODE, SYSTEM)

    /** Called once at app start so every screen opens already in the chosen theme. */
    fun applySaved(context: Context) = AppCompatDelegate.setDefaultNightMode(saved(context))

    /** Saves the choice and switches immediately (open screens recreate themselves). */
    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
