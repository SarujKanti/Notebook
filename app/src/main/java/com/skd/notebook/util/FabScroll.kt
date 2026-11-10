package com.skd.notebook.util

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

private const val PEEK_VISIBLE_FRACTION = 0.5f
private const val SLIDE_DURATION_MS = 200L

/**
 * Brings the button in with a small springy pop when its screen opens, instead of it simply being there.
 * It uses ObjectAnimators, not view.animate(), so the tuck-away below cannot cancel it half-way.
 */
fun View.popIn(delayMs: Long = 250L) {
    scaleX = 0f
    scaleY = 0f
    alpha = 0f
    ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f),
        PropertyValuesHolder.ofFloat(View.ALPHA, 1f)
    ).apply {
        startDelay = delayMs
        duration = 420L
        interpolator = OvershootInterpolator(2.2f)
        start()
    }
}

/**
 * The round, icon-only New Note button: the same tuck-away behaviour, built so the "+" is centered by
 * construction (a plain circle holding a centered icon, so there is no button padding to get wrong).
 *
 * While tucked, half of the circle stays on screen and [icon] slides to the middle of that visible half,
 * so the "+" is never cut off. Tapping the sliver brings the button back instead of creating a note.
 */
fun View.peekCircleOnScroll(recyclerView: RecyclerView, icon: View, onClick: () -> Unit) {
    val fab = this
    var peeking = false
    val threshold = resources.displayMetrics.density * 2

    fun edgeDirection() = if (fab.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f

    fun tuck() {
        if (peeking) return
        peeking = true
        val margin = (fab.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        val hiddenPart = fab.width * (1f - PEEK_VISIBLE_FRACTION) + margin
        fab.animate().cancel()
        fab.animate()
            .translationX(hiddenPart * edgeDirection())
            .setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        // The visible half's middle is a quarter of the circle away from its center.
        icon.animate().cancel()
        icon.animate()
            .translationX(-edgeDirection() * fab.width * (1f - PEEK_VISIBLE_FRACTION) / 2f)
            .setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun reveal() {
        if (!peeking) return
        peeking = false
        fab.animate().cancel()
        fab.animate().translationX(0f).setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator()).start()
        icon.animate().cancel()
        icon.animate().translationX(0f).setDuration(SLIDE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator()).start()
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
