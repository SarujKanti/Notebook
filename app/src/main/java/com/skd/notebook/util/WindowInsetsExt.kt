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

/**
 * Keeps this view's content clear of the status bar (and optionally the navigation bar)
 * without depending on window insets being dispatched to it.
 *
 * Containers such as DrawerLayout/NavigationView consume insets for themselves, so a plain
 * inset listener never fires on some Android versions (seen on Android 16, where the avatar
 * ended up under the clock). Instead this measures where the view really sits on screen and
 * adds only the padding needed to clear the bars — zero when the system already offsets it,
 * so it is safe on every version and never double-pads.
 */
fun View.keepClearOfSystemBars(top: Boolean = true, bottom: Boolean = false) {
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    var appliedTop = 0
    var appliedBottom = 0

    fun update() {
        val root = ViewCompat.getRootWindowInsets(this) ?: return
        val bars = root.getInsets(WindowInsetsCompat.Type.systemBars())
        val loc = IntArray(2)
        getLocationInWindow(loc)

        val needTop = if (top) (bars.top - loc[1]).coerceAtLeast(0) else 0
        val windowBottom = rootView.height - bars.bottom
        val needBottom = if (bottom) (loc[1] + height - windowBottom).coerceAtLeast(0) else 0

        if (needTop != appliedTop || needBottom != appliedBottom) {
            appliedTop = needTop
            appliedBottom = needBottom
            setPadding(paddingLeft, baseTop + needTop, paddingRight, baseBottom + needBottom)
        }
    }

    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    if (isAttachedToWindow) post { update() }
    else addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { v.post { update() } }
        override fun onViewDetachedFromWindow(v: View) {}
    })
}
