package com.skd.notebook.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings

/**
 * Frosted-glass look for every dialog window: the screen behind is dimmed and, on Android 12+
 * phones that allow window blur, blurred too. Elsewhere it simply stays a translucent panel.
 */
internal fun Dialog.applyGlassBackdrop() {
    val w = window ?: return
    w.setDimAmount(0.45f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val attrs = w.attributes
        attrs.blurBehindRadius = (22 * context.resources.displayMetrics.density).toInt()
        w.attributes = attrs
    }
}

/** One selectable row in [showChoiceDialog]. */
class ChoiceOption(
    val label: String,
    val subtitle: String? = null,
    @DrawableRes val icon: Int = 0
)

/**
 * The one dialog design used across the app, in frosted glass: title and subtitle on the left, a
 * round icon on the right, an optional message or custom [content], and a Cancel / action button
 * pair. [destructive] turns the action button red.
 *
 * Pass `positiveText = null` for a dialog that only offers dismiss, or `negativeText = null` for a
 * single-button info dialog.
 */
fun Context.showAppDialog(
    title: String,
    @DrawableRes icon: Int,
    message: String? = null,
    destructive: Boolean = false,
    positiveText: String? = "OK",
    onPositive: () -> Unit = {},
    negativeText: String? = "Cancel",
    content: View? = null,
    onCancel: (() -> Unit)? = null,
    subtitle: String? = null
): AlertDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_app, null)

    view.findViewById<ImageView>(R.id.dialogIcon).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(if (destructive) Color.parseColor("#FF9A93") else Color.WHITE)
    }
    view.findViewById<TextView>(R.id.dialogTitle).text = title
    view.findViewById<TextView>(R.id.dialogSubtitle).apply {
        if (!subtitle.isNullOrBlank()) { text = subtitle; visibility = View.VISIBLE }
    }
    view.findViewById<TextView>(R.id.dialogMessage).apply {
        if (!message.isNullOrBlank()) { text = message; visibility = View.VISIBLE }
    }
    content?.let {
        view.findViewById<FrameLayout>(R.id.dialogContent).apply {
            addView(it)
            visibility = View.VISIBLE
        }
    }

    val dialog = MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
        .setView(view)
        .create()
    // The glass panel draws its own rounded background; the window behind it must be see-through.
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.applyGlassBackdrop()

    // The window around the panel is see-through but still part of the dialog window: treat a tap there as "outside".
    view.setOnClickListener { dialog.cancel() }
    view.findViewById<View>(R.id.dialogPanel).isClickable = true

    var acted = false // set when the action button is used, so dismissing any other way counts as cancel

    val btnNegative = view.findViewById<TextView>(R.id.dialogNegative)
    val btnPositive = view.findViewById<TextView>(R.id.dialogPositive)

    if (negativeText == null) {
        btnNegative.visibility = View.GONE
    } else {
        btnNegative.text = negativeText
        btnNegative.setOnClickListener { dialog.dismiss() }
    }

    if (positiveText == null) {
        btnPositive.visibility = View.GONE
    } else {
        btnPositive.text = positiveText
        if (destructive) btnPositive.setBackgroundResource(R.drawable.bg_glass_danger)
        btnPositive.setOnClickListener {
            acted = true
            dialog.dismiss()
            onPositive()
        }
    }

    // A lone button should span the dialog rather than sit in half of it.
    val lone = when {
        negativeText == null && positiveText == null -> null
        negativeText == null -> btnPositive
        positiveText == null -> btnNegative
        else -> null
    }
    lone?.let { (it.layoutParams as LinearLayout.LayoutParams).apply { marginStart = 0; marginEnd = 0 } }
    if (negativeText == null && positiveText == null) {
        view.findViewById<LinearLayout>(R.id.dialogButtons).visibility = View.GONE
    }

    onCancel?.let { cancel -> dialog.setOnDismissListener { if (!acted) cancel() } }
    dialog.show()
    return dialog
}

/**
 * Single-choice list in the same glass design, with no buttons: tapping an option selects it, applies
 * it and closes the dialog (after a very short moment so the selection can be seen); tapping outside
 * the dialog just closes it. Choosing the option that is already selected only closes it. Each row
 * can carry an icon and a one-line description.
 */
fun Context.showChoiceDialog(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int,
    options: List<ChoiceOption>,
    selectedIndex: Int,
    onPick: (Int) -> Unit
): AlertDialog {
    var selected = selectedIndex.coerceIn(0, options.lastIndex)
    var picked = false // ignore a second tap while the dialog is closing
    lateinit var dialog: AlertDialog
    val inflater = LayoutInflater.from(this)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val rows = mutableListOf<View>()
    val gap = (10 * resources.displayMetrics.density).toInt()

    fun paint() {
        rows.forEachIndexed { index, row ->
            val on = index == selected
            row.setBackgroundResource(if (on) R.drawable.bg_glass_row_selected else R.drawable.bg_glass_row)
            row.findViewById<ImageView>(R.id.choiceMark)
                .setImageResource(if (on) R.drawable.radio_glass_on else R.drawable.radio_glass_off)
        }
    }

    options.forEachIndexed { index, option ->
        val row = inflater.inflate(R.layout.item_dialog_choice, list, false)
        row.findViewById<TextView>(R.id.choiceLabel).text = option.label
        row.findViewById<TextView>(R.id.choiceSubtitle).apply {
            if (option.subtitle == null) visibility = View.GONE else text = option.subtitle
        }
        row.findViewById<ImageView>(R.id.choiceIcon).apply {
            if (option.icon == 0) visibility = View.GONE
            else {
                setImageResource(option.icon)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
        }
        row.setOnClickListener {
            if (picked) return@setOnClickListener
            picked = true
            val changed = index != selected
            selected = index
            paint()
            // Let the new radio mark show for a blink, then close; only a real change is applied.
            row.postDelayed({
                dialog.dismiss()
                if (changed) onPick(index)
            }, CHOICE_CLOSE_DELAY_MS)
        }
        (row.layoutParams as LinearLayout.LayoutParams).bottomMargin = if (index < options.lastIndex) gap else 0
        rows += row
        list.addView(row)
    }
    paint()

    dialog = showAppDialog(
        title = title, subtitle = subtitle, icon = icon,
        positiveText = null, negativeText = null,
        content = list
    )
    return dialog
}

private const val CHOICE_CLOSE_DELAY_MS = 160L

/**
 * Asks "Move to Bin?" first when Delete Confirmation is on in Settings; otherwise just does it.
 * [onCancel] runs if the user backs out, so a swiped row can slide back.
 */
fun Context.confirmMoveToBin(onCancel: () -> Unit = {}, onConfirm: () -> Unit) {
    if (!AppSettings.confirmDelete(this)) {
        onConfirm()
        return
    }
    showAppDialog(
        title = "Move to Bin?",
        icon = R.drawable.ic_delete,
        message = "You can restore it from the Bin later.",
        destructive = true,
        positiveText = "Move to Bin",
        onPositive = onConfirm,
        onCancel = onCancel
    )
}
