package com.skd.notebook.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pads this view's top by the system status bar height, on top of whatever
 * padding it already has. Newer Android versions increasingly force
 * edge-to-edge regardless of windowOptOutEdgeToEdgeEnforcement, which would
 * otherwise let the status bar overlap a fixed-height Toolbar. When the
 * system already reserves space for the status bar (opt-out honored), the
 * reported inset is 0 and this is a no-op.
 */
fun View.fitTopInsetAsPadding() {
    val basePadding = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.setPadding(view.paddingLeft, basePadding + statusBarInset, view.paddingRight, view.paddingBottom)
        insets
    }
}
