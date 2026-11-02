package com.skd.notebook.util

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import com.skd.notebook.R

/**
 * One typeface the user can pick in Settings → Font Style.
 * [font] is a font family in res/font, or 0 for the phone's own font (the starting choice).
 */
enum class FontStyle(
    val key: String,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    @FontRes val font: Int
) {
    DEVICE("device", R.string.font_device, R.string.font_device_hint, 0),
    MODERN("modern", R.string.font_modern, R.string.font_modern_hint, R.font.font_poppins),
    CLEAN("clean", R.string.font_clean, R.string.font_clean_hint, R.font.font_lato),
    FRIENDLY("friendly", R.string.font_friendly, R.string.font_friendly_hint, R.font.font_abeezee),
    CLASSIC("classic", R.string.font_classic, R.string.font_classic_hint, R.font.font_pt_serif),
    ELEGANT("elegant", R.string.font_elegant, R.string.font_elegant_hint, R.font.font_crimson_text),
    SLAB("slab", R.string.font_slab, R.string.font_slab_hint, R.font.font_arvo),
    TYPEWRITER("typewriter", R.string.font_typewriter, R.string.font_typewriter_hint, R.font.font_courier_prime),
    HANDWRITTEN("handwritten", R.string.font_handwritten, R.string.font_handwritten_hint, R.font.font_indie_flower),
    SCRIPT("script", R.string.font_script, R.string.font_script_hint, R.font.font_pacifico),
    DISPLAY("display", R.string.font_display, R.string.font_display_hint, R.font.font_lobster);

    /** The typeface family, or null for the phone's own font. */
    fun typeface(context: Context): Typeface? =
        if (font == 0) null else runCatching { ResourcesCompat.getFont(context, font) }.getOrNull()
}

/**
 * The font style chosen in Settings, saved on the device, and the code that puts it on screen.
 * Everything in the app keeps the phone's font until a style is picked.
 */
object FontStyles {
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_FONT_STYLE = "font_style"

    fun saved(context: Context): FontStyle {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_FONT_STYLE, null)
        return FontStyle.values().firstOrNull { it.key == key } ?: FontStyle.DEVICE
    }

    fun set(context: Context, style: FontStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_FONT_STYLE, style.key).apply()
    }

    /** Puts the saved style on [view] if it shows text, keeping its bold / italic. Nothing for the phone's font. */
    fun applyTo(view: View?, typeface: Typeface?) {
        if (typeface == null || view !is TextView) return
        view.setTypeface(typeface, view.typeface?.style ?: Typeface.NORMAL)
    }

    /** Puts the saved style on every piece of text under [root] (for views built in code). */
    fun applyToTree(root: View?) {
        val context = root?.context ?: return
        val typeface = saved(context).typeface(context) ?: return
        walk(root, typeface)
    }

    private fun walk(view: View?, typeface: Typeface) {
        applyTo(view, typeface)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), typeface)
    }
}
