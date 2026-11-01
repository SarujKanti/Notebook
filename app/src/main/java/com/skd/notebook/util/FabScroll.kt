package com.skd.notebook.util

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

private const val PEEK_VISIBLE_FRACTION = 0.5f
private const val SLIDE_DURATION_MS = 200L

/**
 * Tucks this FAB against the screen edge while [recyclerView] scrolls down, and brings it back
 * on scroll up, at the top of the list, or when the peeking button is tapped.
 *
 * Tucked means: shrunk to its icon and slid sideways until only [PEEK_VISIBLE_FRACTION] of it (+ icon included)
 * stays on screen. Tapping the visible sliver reveals the button instead of triggering
 * [onClick], so a swipe or tap near the edge (where the system back gesture lives) can't
 * create a note by accident.
 */
fun ExtendedFloatingActionButton.peekOnScroll(recyclerView: RecyclerView, onClick: () -> Unit) {
    val fab = this
    var peeking = false
    val threshold = resources.displayMetrics.density * 2

    fun edgeDirection() = if (fab.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f

    val restPaddingStart = fab.paddingStart
    val restIconGravity = fab.iconGravity
    val peekIconInset = (resources.displayMetrics.density * 2).toInt()

    // Centered, half of the + would be off screen; park it on the side that stays visible.
    fun parkIconOnVisibleSide(park: Boolean) {
        fab.iconGravity = if (park) MaterialButton.ICON_GRAVITY_START else restIconGravity
        fab.setPaddingRelative(if (park) peekIconInset else restPaddingStart, fab.paddingTop, fab.paddingEnd, fab.paddingBottom)
    }

    fun slideToEdge() {
        parkIconOnVisibleSide(true)
        val margin = (fab.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        val hiddenPart = fab.width * (1f - PEEK_VISIBLE_FRACTION) + margin
        fab.animate().cancel()
        fab.animate()
            .translationX(hiddenPart * edgeDirection())
            .setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun tuck() {
        if (peeking) return
        peeking = true
        if (!fab.isExtended) {
            slideToEdge()
            return
        }
        fab.shrink(object : ExtendedFloatingActionButton.OnChangedCallback() {
            override fun onShrunken(fab: ExtendedFloatingActionButton) {
                // Width is only final once the shrink is done; skip if the user already scrolled back.
                if (peeking) slideToEdge()
            }
        })
    }

    fun reveal() {
        if (!peeking) return
        peeking = false
        parkIconOnVisibleSide(false)
        fab.animate().cancel()
        fab.animate()
            .translationX(0f)
            .setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        fab.extend()
    }

    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            when {
                !rv.canScrollVertically(-1) -> reveal()
                dy > threshold -> tuck()
                dy < -threshold -> reveal()
            }
        }
    })

    setOnClickListener { if (peeking) reveal() else onClick() }
}
