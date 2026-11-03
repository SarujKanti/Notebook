package com.skd.notebook.ui

import com.skd.notebook.util.dimen
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.skd.notebook.R
import kotlin.math.max

/**
 * The navigation drawer's background. It paints, behind the drawer's content:
 *  - the body color,
 *  - a sunset-over-mountains picture across the header (the view with id navHeader), cut along a wavy
 *    bottom edge that has a soft purple glow line on it,
 *  - one more glowing wave above the Logout button.
 * Everything is drawn in code, so it stays sharp on any screen and needs no image file.
 */
class DrawerPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scenePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val footerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val clipPath = Path()
    private val edgePath = Path()
    private val footerEdge = Path()
    private val footerArea = Path()

    // The picture is drawn on a SCENE_W x SCENE_H canvas, then scaled to fill the header (cropping the extra).
    private val farMountains = Path()
    private val nearMountains = Path()
    private val skyShader: Shader
    private val glowShader: Shader
    private val farShader: Shader
    private val nearShader: Shader

    private val edgeColor = ContextCompat.getColor(context, R.color.drawer_wave_edge)

    init {
        setWillNotDraw(false)
        bodyPaint.color = ContextCompat.getColor(context, R.color.drawer_body)

        fun color(id: Int) = ContextCompat.getColor(context, id)
        skyShader = LinearGradient(
            0f, 0f, 0f, SCENE_H,
            intArrayOf(color(R.color.drawer_scene_sky_top), color(R.color.drawer_scene_sky_mid),
                color(R.color.drawer_scene_sky_glow), color(R.color.drawer_scene_horizon)),
            floatArrayOf(0f, 0.42f, 0.66f, 0.82f), Shader.TileMode.CLAMP
        )
        glowShader = RadialGradient(
            GLOW_X, GLOW_Y, GLOW_R,
            intArrayOf(ColorUtils.setAlphaComponent(color(R.color.drawer_scene_horizon), 235),
                ColorUtils.setAlphaComponent(color(R.color.drawer_scene_sky_glow), 90), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        farShader = LinearGradient(
            0f, 85f, 0f, SCENE_H,
            color(R.color.drawer_scene_mountain_far), color(R.color.drawer_scene_mountain_near), Shader.TileMode.CLAMP
        )
        nearShader = LinearGradient(
            0f, 150f, 0f, SCENE_H,
            color(R.color.drawer_scene_mountain_near), color(R.color.drawer_scene_mountain_front), Shader.TileMode.CLAMP
        )
        starPaint.color = 0xFFFFFFFF.toInt()

        val soften = CornerPathEffect(5f)
        scenePaint.pathEffect = soften

        farMountains.apply {
            moveTo(0f, 150f); lineTo(55f, 128f); lineTo(95f, 142f); lineTo(150f, 112f); lineTo(205f, 128f)
            lineTo(250f, 104f); lineTo(278f, 86f); lineTo(304f, 108f); lineTo(336f, 98f); lineTo(SCENE_W, 114f)
            lineTo(SCENE_W, SCENE_H); lineTo(0f, SCENE_H); close()
        }
        nearMountains.apply {
            moveTo(0f, 186f); lineTo(70f, 160f); lineTo(130f, 178f); lineTo(200f, 150f); lineTo(262f, 174f)
            lineTo(330f, 158f); lineTo(SCENE_W, 170f); lineTo(SCENE_W, SCENE_H); lineTo(0f, SCENE_H); close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val headerHeight = measureHeader()

        canvas.drawRect(0f, 0f, w, h, bodyPaint)
        drawFooterWave(canvas, w, h)
        if (headerHeight <= 0f) return

        canvas.save()
        canvas.translate(0f, headerTop)
        buildHeaderShape(w, headerHeight)

        canvas.save()
        canvas.clipPath(clipPath)
        drawScene(canvas, w, headerHeight)
        canvas.restore()

        // The glowing line along the header's bottom edge.
        glowPaint.strokeWidth = context.dimen(R.dimen.dp_8)
        glowPaint.color = ColorUtils.setAlphaComponent(edgeColor, 60)
        canvas.drawPath(edgePath, glowPaint)
        edgePaint.strokeWidth = context.dimen(R.dimen.dp_2)
        edgePaint.color = edgeColor
        canvas.drawPath(edgePath, edgePaint)
        canvas.restore()
    }

    /** The header's wavy bottom edge: high on the right, lowest a little left of the middle, then lifting on the left. */
    private fun buildHeaderShape(w: Float, hb: Float) {
        val left = hb - context.dimen(R.dimen.dp_26)
        val right = hb - context.dimen(R.dimen.dp_56)
        val rise = context.dimen(R.dimen.dp_14)
        val lift = context.dimen(R.dimen.dp_34)

        edgePath.reset()
        edgePath.moveTo(0f, left)
        edgePath.cubicTo(w * 0.12f, hb - rise, w * 0.30f, hb, w * 0.48f, hb)
        edgePath.cubicTo(w * 0.62f, hb, w * 0.75f, hb - lift, w, right)

        clipPath.reset()
        clipPath.moveTo(0f, 0f)
        clipPath.lineTo(w, 0f)
        clipPath.lineTo(w, right)
        clipPath.cubicTo(w * 0.75f, hb - lift, w * 0.62f, hb, w * 0.48f, hb)
        clipPath.cubicTo(w * 0.30f, hb, w * 0.12f, hb - rise, 0f, left)
        clipPath.close()
    }

    private fun drawScene(canvas: Canvas, w: Float, hb: Float) {
        val scale = max(w / SCENE_W, hb / SCENE_H)
        canvas.save()
        canvas.translate((w - SCENE_W * scale) / 2f, (hb - SCENE_H * scale) / 2f)
        canvas.scale(scale, scale)

        scenePaint.shader = skyShader
        canvas.drawRect(0f, 0f, SCENE_W, SCENE_H, scenePaint)
        scenePaint.shader = glowShader
        canvas.drawRect(0f, 0f, SCENE_W, SCENE_H, scenePaint)

        STARS.forEach { (x, y, r) -> canvas.drawCircle(x, y, r, starPaint.apply { alpha = 150 }) }

        scenePaint.shader = farShader
        canvas.drawPath(farMountains, scenePaint)
        scenePaint.shader = nearShader
        canvas.drawPath(nearMountains, scenePaint)
        scenePaint.shader = null
        canvas.restore()
    }

    /**
     * A second glowing wave between the version line and the Logout button. It is measured from the button,
     * not from the panel's bottom, so it stays above Logout (and scrolls with it) however tall the panel is.
     */
    private fun drawFooterWave(canvas: Canvas, w: Float, h: Float) {
        val authTop = measureAuthButtonTop() ?: (h - context.dimen(R.dimen.dp_56))
        val left = authTop - context.dimen(R.dimen.dp_40)
        val low = authTop - context.dimen(R.dimen.dp_12)
        val right = authTop - context.dimen(R.dimen.dp_50)

        footerEdge.reset()
        footerEdge.moveTo(0f, left)
        footerEdge.cubicTo(w * 0.22f, left + (low - left) * 0.2f, w * 0.34f, low, w * 0.55f, low)
        footerEdge.cubicTo(w * 0.78f, low, w * 0.9f, right + (low - right) * 0.1f, w, right)

        footerArea.set(footerEdge)
        footerArea.lineTo(w, h)
        footerArea.lineTo(0f, h)
        footerArea.close()
        footerFillPaint.color = ColorUtils.setAlphaComponent(edgeColor, 28)
        canvas.drawPath(footerArea, footerFillPaint)

        glowPaint.strokeWidth = context.dimen(R.dimen.dp_8)
        glowPaint.color = ColorUtils.setAlphaComponent(edgeColor, 40)
        canvas.drawPath(footerEdge, glowPaint)
        edgePaint.strokeWidth = context.dimen(R.dimen.dp_2)
        edgePaint.color = ColorUtils.setAlphaComponent(edgeColor, 200)
        canvas.drawPath(footerEdge, edgePaint)
    }

    private var headerTop = 0f

    /**
     * Sets [headerTop] (the header's top in this view's own coordinates, which moves while the panel's
     * content scrolls) and returns the header's height, or 0 if there is no header yet.
     */
    private fun measureHeader(): Float {
        val header = findViewById<View>(R.id.navHeader) ?: return 0f
        val rect = Rect(0, 0, header.width, header.height)
        offsetDescendantRectToMyCoords(header, rect)
        headerTop = rect.top.toFloat()
        return header.height.toFloat()
    }

    /** The Logout / Login button's top in this view's own coordinates (it moves while the panel scrolls), or null. */
    private fun measureAuthButtonTop(): Float? {
        val button = findViewById<View>(R.id.btnNavAuth) ?: return null
        if (button.height == 0) return null
        val rect = Rect(0, 0, button.width, button.height)
        offsetDescendantRectToMyCoords(button, rect)
        return rect.top.toFloat()
    }

    private val repaintOnScroll = ViewTreeObserver.OnScrollChangedListener { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(repaintOnScroll)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnScrollChangedListener(repaintOnScroll)
        super.onDetachedFromWindow()
    }

    // The header's size is only known after layout; repaint then.
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        invalidate()
    }

    private companion object {
        const val SCENE_W = 360f
        const val SCENE_H = 240f
        const val GLOW_X = 262f
        const val GLOW_Y = 118f
        const val GLOW_R = 150f

        /** Star x, y and radius, on the scene canvas. */
        val STARS = listOf(
            Triple(38f, 28f, 1.2f), Triple(92f, 52f, 1f), Triple(148f, 22f, 1.3f), Triple(206f, 40f, 1f),
            Triple(300f, 24f, 1.2f), Triple(336f, 58f, 1f), Triple(64f, 84f, 0.9f), Triple(180f, 70f, 0.9f)
        )
    }
}
