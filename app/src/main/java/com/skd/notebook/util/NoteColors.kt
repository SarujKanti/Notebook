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

    /** Colors offered in every note editor. "" = no color (plain surface). */
    val PALETTE = listOf(
        "",        "#FFCDD2", "#F8BBD9", "#FFE0B2", "#FFF9C4",
        "#DCEDC8", "#B2EBF2", "#BBDEFB", "#E1BEE7", "#D7CCC8",
        "#FFAB91", "#FFECB3", "#F0F4C3", "#C8E6C9", "#B2DFDB",
        "#B3E5FC", "#C5CAE9", "#D1C4E9", "#F5C6E8", "#CFD8DC"
    )

    fun background(context: Context, hex: String): Int {
        val surface = ContextCompat.getColor(context, R.color.colorSurface)
        if (hex.isEmpty()) return surface
        val pastel = runCatching { Color.parseColor(hex) }.getOrNull() ?: return surface
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) ColorUtils.blendARGB(surface, pastel, 0.32f) else pastel
    }
}
