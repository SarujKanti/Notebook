package com.skd.notebook.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.skd.notebook.R

/**
 * Background color for a note (card or editor).
 *
 * Notes store light pastel colors. In dark mode those would glare and make the theme's light
 * text unreadable, so they are shown as a deeper tint of the same hue instead; that keeps
 * every title, body and icon using the normal theme colors in both modes.
 */
object NoteColors {

    fun background(context: Context, hex: String): Int {
        val surface = ContextCompat.getColor(context, R.color.colorSurface)
        if (hex.isEmpty()) return surface
        val pastel = runCatching { Color.parseColor(hex) }.getOrNull() ?: return surface
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) ColorUtils.blendARGB(surface, pastel, 0.32f) else pastel
    }
}
