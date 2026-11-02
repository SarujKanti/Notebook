package com.skd.notebook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.skd.notebook.R

/**
 * A flat-topped hexagon with softly rounded corners that holds its children (the floating "+" buttons).
 * It fills itself with [R.styleable.HexagonLayout_hexColor], casts a shadow in its own shape, and
 * clips touch ripples to it. Size it with a width of about 1.15 x its height for a regular hexagon.
 */
class HexagonLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val cornerRadius: Float

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.HexagonLayout)
        fill.color = a.getColor(R.styleable.HexagonLayout_hexColor, ContextCompat.getColor(context, R.color.fab_home))
        cornerRadius = a.getDimension(
            R.styleable.HexagonLayout_hexCornerRadius,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics)
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
        invalidateOutline()
    }

    /** Six corners (left and right points, flat top and bottom), each rounded with a small curve. */
    private fun buildShape(w: Float, h: Float) {
        val corners = listOf(
            w * 0.25f to 0f, w * 0.75f to 0f, w to h / 2f,
            w * 0.75f to h, w * 0.25f to h, 0f to h / 2f
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
    }
}
