package com.skd.notebook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.skd.notebook.R

/**
 * A regular octagon with softly rounded corners that holds its children (the floating "+" buttons).
 * It fills itself with [R.styleable.OctagonLayout_octColor], casts a shadow in its own shape, and
 * clips touch ripples to it. Give it a square size for a regular octagon.
 */
class OctagonLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val cornerRadius: Float
    private val startColor: Int
    private val endColor: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.OctagonLayout)
        startColor = a.getColor(R.styleable.OctagonLayout_octColor, ContextCompat.getColor(context, R.color.fab_home))
        endColor = a.getColor(R.styleable.OctagonLayout_octColorEnd, startColor)
        fill.color = startColor
        if (Build.VERSION.SDK_INT >= 28 && a.hasValue(R.styleable.OctagonLayout_octGlow)) {
            val glow = a.getColor(R.styleable.OctagonLayout_octGlow, startColor)
            outlineSpotShadowColor = glow
            outlineAmbientShadowColor = glow
        }
        cornerRadius = a.getDimension(
            R.styleable.OctagonLayout_octCornerRadius,
            resources.getDimension(R.dimen.dp_8)
        )
        a.recycle()

        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (!shape.isEmpty) outline.setConvexPath(shape)
            }
        }
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            foreground = ContextCompat.getDrawable(context, ripple.resourceId)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShape(w.toFloat(), h.toFloat())
        fill.shader = if (endColor != startColor) LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), startColor, endColor, Shader.TileMode.CLAMP) else null
        // Light falling from above: a white veil on the top part of the button that fades out toward the middle.
        sheen.shader = LinearGradient(0f, 0f, 0f, h * SHEEN_REACH, SHEEN_TOP, 0x00FFFFFF, Shader.TileMode.CLAMP)
        invalidateOutline()
    }

    /** Eight corners (flat top, bottom, left and right sides), each rounded with a small curve. */
    private fun buildShape(w: Float, h: Float) {
        val corners = listOf(
            w * INSET to 0f, w * (1f - INSET) to 0f, w to h * INSET, w to h * (1f - INSET),
            w * (1f - INSET) to h, w * INSET to h, 0f to h * (1f - INSET), 0f to h * INSET
        )
        shape.reset()
        corners.forEachIndexed { i, (x, y) ->
            val (px, py) = corners[(i + corners.size - 1) % corners.size]
            val (nx, ny) = corners[(i + 1) % corners.size]
            val inX = x + (px - x) * cutFraction(x, y, px, py)
            val inY = y + (py - y) * cutFraction(x, y, px, py)
            val outX = x + (nx - x) * cutFraction(x, y, nx, ny)
            val outY = y + (ny - y) * cutFraction(x, y, nx, ny)
            if (i == 0) shape.moveTo(inX, inY) else shape.lineTo(inX, inY)
            shape.quadTo(x, y, outX, outY)
        }
        shape.close()
    }

    /** How far along an edge of the given length the rounding of a corner reaches (as a fraction of it). */
    private fun cutFraction(x: Float, y: Float, ox: Float, oy: Float): Float {
        val length = Math.hypot((ox - x).toDouble(), (oy - y).toDouble()).toFloat()
        return if (length <= 0f) 0f else (cornerRadius / length).coerceAtMost(0.5f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(shape, fill)
        canvas.drawPath(shape, sheen)
    }
}

/** How far down the button the highlight reaches, and how bright it is at the very top. */
private const val SHEEN_REACH = 0.62f
private const val SHEEN_TOP = 0x66FFFFFF

/** How far in from each corner of the bounding box an octagon's corners sit: 1 / (2 + sqrt 2). */
private const val INSET = 0.2929f
