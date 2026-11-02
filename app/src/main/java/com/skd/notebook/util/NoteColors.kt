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
 * text unreadable, so they are shown as a deep shade of the same hue instead; that keeps
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

    private fun isNight(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun background(context: Context, hex: String): Int {
        val surface = ContextCompat.getColor(context, R.color.colorSurface)
        if (hex.isEmpty()) return surface
        val pastel = runCatching { Color.parseColor(hex) }.getOrNull() ?: return surface
        if (!isNight(context)) return pastel

        // Same hue, kept clearly colored but dark: lightness is pulled down to a fixed deep level
        // (so every color reads equally well under light text) and saturation is eased a little.
        // Mixing the pastel into the dark surface instead washed every color out to a similar grey.
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(pastel, hsl)
        hsl[1] = (hsl[1] * 0.8f).coerceAtMost(0.62f)
        hsl[2] = NIGHT_LIGHTNESS
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * A folder's accent (icon, stripe, chip). The palette colors are tuned for a light screen; on a
     * dark one the deeper ones (indigo, brown, blue-grey...) nearly vanish, so they are lightened
     * (same hue) until they stand out. [hex] "" or unreadable gives the app's primary color.
     */
    fun folderAccent(context: Context, hex: String): Int {
        val primary = ContextCompat.getColor(context, R.color.colorPrimary)
        if (hex.isEmpty()) return primary
        val color = runCatching { Color.parseColor(hex) }.getOrNull() ?: return primary
        if (!isNight(context)) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(NIGHT_ACCENT_MIN_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Fill of a color-picker circle: the color the note will really show (so deep shades in dark mode). */
    fun swatch(context: Context, hex: String): Int = background(context, hex)

    /** Outline of a color-picker circle; the selected one is stronger. Visible on both light and dark sheets. */
    fun swatchStroke(context: Context, selected: Boolean): Int =
        if (isNight(context)) { if (selected) Color.WHITE else 0x66FFFFFF } else { if (selected) Color.DKGRAY else Color.LTGRAY }

    private const val NIGHT_LIGHTNESS = 0.30f
    private const val NIGHT_ACCENT_MIN_LIGHTNESS = 0.64f
}
