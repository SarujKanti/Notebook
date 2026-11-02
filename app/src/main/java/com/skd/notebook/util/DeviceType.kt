package com.skd.notebook.util

import android.content.Context
import android.content.res.Configuration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.skd.notebook.R

/** True on a tablet (shortest screen side of 600dp or more). Phones always get false and keep their own layout. */
fun Context.isTablet(): Boolean =
    resources.configuration.smallestScreenWidthDp >= resources.getInteger(R.integer.tablet_min_width_dp)

fun Context.isLandscape(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * How many notes sit side by side in the grid: a phone keeps its own count,
 * a tablet shows 4 in landscape and 3 in portrait.
 */
fun Context.noteGridColumns(): Int =
    if (isTablet()) {
        resources.getInteger(if (isLandscape()) R.integer.grid_span_tablet_landscape else R.integer.grid_span_tablet_portrait)
    } else {
        resources.getInteger(R.integer.grid_span_count)
    }

/** On a tablet the note sheet spans the whole screen (Material stops it at 640dp); a phone's sheet is left alone. */
fun BottomSheetBehavior<*>.fillWidthOnTablet(context: Context) {
    if (context.isTablet()) maxWidth = -1 // -1: no width limit
}
