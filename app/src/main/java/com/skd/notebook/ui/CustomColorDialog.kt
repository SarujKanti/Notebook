package com.skd.notebook.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.skd.notebook.R

/**
 * Lets the user mix their own color with hue / saturation / brightness sliders and
 * hands it back as "#RRGGBB".
 */
fun Context.showCustomColorDialog(initialColor: Int, onPicked: (String) -> Unit) {
    val dp = resources.displayMetrics.density
    val hsv = FloatArray(3).also { Color.colorToHSV(initialColor, it) }

    val preview = View(this)
    fun currentColor() = Color.HSVToColor(hsv)
    fun refresh() = preview.setBackgroundColor(currentColor())

    fun slider(label: String, max: Int, start: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setPadding(0, (12 * dp).toInt(), 0, 0)
        })
        row.addView(SeekBar(this).apply {
            this.max = max
            progress = start
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    onChange(value); refresh()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        return row
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt()))
        addView(slider("Color", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() })
        addView(slider("Intensity", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f })
        addView(slider("Brightness", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f })
    }
    refresh()

    showAppDialog(
        title = "Custom color",
        icon = R.drawable.ic_theme,
        positiveText = "Add",
        onPositive = { onPicked(String.format("#%06X", 0xFFFFFF and currentColor())) },
        content = content
    )
}
