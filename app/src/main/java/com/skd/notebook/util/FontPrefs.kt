package com.skd.notebook.util

import android.content.Context
import android.content.res.Configuration

/**
 * Small / Medium / Large text size for the whole app, saved on the device.
 *
 * It scales on top of the phone's own font size setting rather than replacing it, so someone who
 * already uses a large system font still gets a bigger "Large" than a "Medium".
 */
object FontPrefs {
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_FONT_SIZE = "font_size"

    const val SMALL = 0
    const val MEDIUM = 1
    const val LARGE = 2

    val LABELS = listOf("Small", "Medium", "Large")
    private val FACTORS = floatArrayOf(0.88f, 1f, 1.18f)

    fun saved(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_FONT_SIZE, MEDIUM)
            .coerceIn(SMALL, LARGE)

    fun set(context: Context, size: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_FONT_SIZE, size).apply()
    }

    /** Context whose text sizes (sp) follow the saved choice. Medium leaves the context untouched. */
    fun wrap(base: Context): Context {
        val factor = FACTORS[saved(base)]
        if (factor == 1f) return base
        val config = Configuration(base.resources.configuration)
        config.fontScale *= factor
        return base.createConfigurationContext(config)
    }
}
