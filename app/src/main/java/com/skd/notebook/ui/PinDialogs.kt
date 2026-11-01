package com.skd.notebook.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.skd.notebook.R

/**
 * A glass dialog with one or more PIN boxes (one per entry in [fields], each entry is the box's hint).
 *
 * [validate] gets what was typed, in order. Return null to accept (the dialog closes) or an error
 * message to show under the boxes and keep the dialog open; the boxes are cleared after an error.
 * [onForgot], if given, adds a "Forgot PIN?" link that closes the dialog and calls it.
 * [onAccepted] runs after the dialog has closed on an accepted entry.
 * [onCancel] runs when the dialog goes away without being accepted (Cancel, back, or a tap outside
 * when [cancelable] is true), but not for "Forgot PIN?".
 */
fun Context.showPinDialog(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int,
    fields: List<String>,
    positiveText: String,
    negativeText: String? = "Cancel",
    cancelable: Boolean = true,
    onForgot: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onAccepted: () -> Unit = {},
    validate: (List<String>) -> String?
): AlertDialog {
    val glass = ContextThemeWrapper(this, R.style.ThemeOverlay_NoteBook_GlassSheet)
    val dp = resources.displayMetrics.density

    val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val boxes = fields.map { hint ->
        val layout = LayoutInflater.from(glass).inflate(R.layout.view_pin_field, column, false) as TextInputLayout
        layout.hint = hint
        column.addView(layout)
        layout.findViewById<TextInputEditText>(R.id.etPin)
    }
    boxes.dropLast(1).forEach { it.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT }
    boxes.last().imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE

    val error = TextView(this).apply {
        setTextColor(0xFFFF9A93.toInt())
        textSize = 14f
        setPadding((4 * dp).toInt(), 0, 0, (8 * dp).toInt())
        visibility = View.GONE
    }
    column.addView(error)

    var forgot: TextView? = null
    var dialog: AlertDialog? = null
    var leavingForForgot = false
    if (onForgot != null) {
        forgot = TextView(this).apply {
            text = "Forgot PIN?"
            textSize = 14f
            setTextColor(0xFFCDBBFF.toInt())
            setPadding((4 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                leavingForForgot = true
                dialog?.dismiss()
                onForgot()
            }
        }
        column.addView(forgot)
    }

    fun submit(): Boolean {
        val message = validate(boxes.map { it.text?.toString().orEmpty() })
        if (message == null) return true
        error.text = message
        error.visibility = View.VISIBLE
        boxes.forEach { it.text = null }
        boxes.first().requestFocus()
        return false
    }

    val shown = showAppDialog(
        title = title, subtitle = subtitle, icon = icon,
        positiveText = positiveText, negativeText = negativeText,
        content = column,
        onPositive = onAccepted,
        cancelable = cancelable,
        onPositiveValidated = { submit() },
        onCancel = { if (!leavingForForgot) onCancel?.invoke() }
    )
    dialog = shown
    // The keyboard's Done key acts like the action button (one validation, one way of closing).
    boxes.last().setOnEditorActionListener { _, actionId, _ ->
        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
            shown.findViewById<View>(R.id.dialogPositive)?.performClick()
            true
        } else false
    }
    shown.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    boxes.first().requestFocus()
    return shown
}
