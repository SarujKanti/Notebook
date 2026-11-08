package com.skd.notebook.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.skd.notebook.R
import java.util.Collections
import java.util.WeakHashMap

/**
 * A placeholder shown where content is still loading: a quiet rounded shape with a light band that
 * sweeps across it. Every block on screen shares one clock and measures the band across the whole
 * window, so a screen full of blocks shimmers as one sweep, not as many separate ones.
 *
 * Give it a size and (with app:skeletonRadius) a corner radius; app:skeletonCircle makes it round.
 */
class SkeletonBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val base = ContextCompat.getColor(context, R.color.skeleton_base)
    private val highlight = ContextCompat.getColor(context, R.color.skeleton_highlight)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = RectF()
    private val matrix = Matrix()
    private val where = IntArray(2)
    private var radius = 0f
    private var circle = false

    // The band: base, highlight, base. It is BAND_WIDTH wide and moved across the window by the shared clock.
    private val band = LinearGradient(
        0f, 0f, BAND_WIDTH, 0f, intArrayOf(base, highlight, base), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
    )

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SkeletonBlock)
        try {
            radius = a.getDimension(R.styleable.SkeletonBlock_skeletonRadius, 0f)
            circle = a.getBoolean(R.styleable.SkeletonBlock_skeletonCircle, false)
        } finally {
            a.recycle()
        }
        paint.color = base
        paint.shader = band
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SkeletonClock.register(this)
    }

    override fun onDetachedFromWindow() {
        SkeletonClock.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        shape.set(0f, 0f, width.toFloat(), height.toFloat())
        getLocationInWindow(where)
        val windowWidth = rootView.width.toFloat().coerceAtLeast(1f)
        // Where the band's left edge is, in window coordinates, then relative to this block.
        val bandLeft = -BAND_WIDTH + SkeletonClock.phase * (windowWidth + BAND_WIDTH)
        matrix.setTranslate(bandLeft - where[0], 0f)
        band.setLocalMatrix(matrix)
        if (circle) {
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, paint)
        } else {
            canvas.drawRoundRect(shape, radius, radius, paint)
        }
    }

    private companion object {
        const val BAND_WIDTH = 600f
    }
}

/** One animation for every [SkeletonBlock] on screen; it runs only while at least one is attached. */
internal object SkeletonClock {
    private val blocks: MutableSet<SkeletonBlock> = Collections.newSetFromMap(WeakHashMap())
    private var animator: ValueAnimator? = null

    /** 0 → 1 over each sweep. */
    var phase = 0f
        private set

    fun register(block: SkeletonBlock) {
        blocks.add(block)
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    blocks.forEach { block -> block.invalidate() }
                }
                start()
            }
        }
    }

    fun unregister(block: SkeletonBlock) {
        blocks.remove(block)
        if (blocks.isEmpty()) {
            animator?.cancel()
            animator = null
        }
    }
}
