package com.skd.notebook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.skd.notebook.R

/**
 * The navigation drawer's background. It paints, behind the drawer's content:
 *  - a light body,
 *  - the purple gradient header (with a soft highlight in the corner) ending at the bottom of the
 *    view with id navHeader, with a rounded bottom-right corner and a notch on the left where the
 *    menu sheet's rounded top-left corner overlaps it,
 *  - two faint waves along the bottom.
 */
class DrawerPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val corner = 40 * density

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPath = Path()
    private val blobPath = Path()
    private val wavePath = Path()

    private val headStart = ContextCompat.getColor(context, R.color.drawer_head_start)
    private val headEnd = ContextCompat.getColor(context, R.color.drawer_head_end)

    init {
        setWillNotDraw(false)
        bodyPaint.color = ContextCompat.getColor(context, R.color.drawer_body)
        blobPaint.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.drawer_blob), 90)
        wavePaint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val headerBottom = findViewById<View>(R.id.navHeader)?.bottom?.toFloat() ?: 0f

        canvas.drawRect(0f, 0f, w, h, bodyPaint)
        drawWaves(canvas, w, h)
        if (headerBottom <= 0f) return

        // Header: rounded bottom-right corner, notch at the bottom-left for the sheet's rounded corner.
        headPath.reset()
        headPath.moveTo(0f, 0f)
        headPath.lineTo(w, 0f)
        headPath.lineTo(w, headerBottom - corner)
        headPath.quadTo(w, headerBottom, w - corner, headerBottom)
        headPath.lineTo(corner, headerBottom)
        headPath.quadTo(0f, headerBottom, 0f, headerBottom + corner)
        headPath.close()

        headPaint.shader = LinearGradient(0f, 0f, w, headerBottom, headStart, headEnd, Shader.TileMode.CLAMP)
        canvas.drawPath(headPath, headPaint)

        // Soft highlight swoosh in the top-left corner, kept inside the header shape.
        canvas.save()
        canvas.clipPath(headPath)
        blobPath.reset()
        blobPath.moveTo(0f, 0f)
        blobPath.lineTo(w * 0.70f, 0f)
        blobPath.cubicTo(w * 0.52f, headerBottom * 0.12f, w * 0.20f, headerBottom * 0.10f, 0f, headerBottom * 0.36f)
        blobPath.close()
        canvas.drawPath(blobPath, blobPaint)
        canvas.restore()
    }

    private fun drawWaves(canvas: Canvas, w: Float, h: Float) {
        wavePaint.alpha = 22
        wavePath.reset()
        wavePath.moveTo(0f, h - 250 * density)
        wavePath.cubicTo(w * 0.30f, h - 310 * density, w * 0.62f, h - 190 * density, w, h - 262 * density)
        wavePath.lineTo(w, h)
        wavePath.lineTo(0f, h)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)

        wavePaint.alpha = 34
        wavePath.reset()
        wavePath.moveTo(0f, h - 200 * density)
        wavePath.cubicTo(w * 0.28f, h - 150 * density, w * 0.66f, h - 255 * density, w, h - 175 * density)
        wavePath.lineTo(w, h)
        wavePath.lineTo(0f, h)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)
    }

    // The header's size is only known after layout; repaint then.
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        invalidate()
    }
}
