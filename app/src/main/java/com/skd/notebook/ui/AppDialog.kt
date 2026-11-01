package com.skd.notebook.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skd.notebook.R

/**
 * The one dialog design used across the app: a round icon badge, centered title and message,
 * optional custom [content], and a Cancel / action button pair. [destructive] switches the
 * badge and the action button to red.
 *
 * Pass `positiveText = null` for a dialog that only offers dismiss (choice lists), or
 * `negativeText = null` for a single-button info dialog.
 */
fun Context.showAppDialog(
    title: String,
    @DrawableRes icon: Int,
    message: String? = null,
    destructive: Boolean = false,
    positiveText: String? = "OK",
    onPositive: () -> Unit = {},
    negativeText: String? = "Cancel",
    content: View? = null
): AlertDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_app, null)

    val tone = if (destructive) ContextCompat.getColor(this, R.color.color_red_700)
               else MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)

    view.findViewById<FrameLayout>(R.id.dialogBadge).background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ColorUtils.setAlphaComponent(tone, 0x2E))
    }
    view.findViewById<ImageView>(R.id.dialogIcon).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tone)
    }
    view.findViewById<TextView>(R.id.dialogTitle).text = title
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

    val btnNegative = view.findViewById<MaterialButton>(R.id.dialogNegative)
    val btnPositive = view.findViewById<MaterialButton>(R.id.dialogPositive)

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
        if (destructive) {
            btnPositive.backgroundTintList = ColorStateList.valueOf(tone)
            btnPositive.setTextColor(Color.WHITE)
        }
        btnPositive.setOnClickListener {
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

    dialog.show()
    return dialog
}

/** Single-choice list in the same design; picking a row applies it and closes the dialog. */
fun Context.showChoiceDialog(
    title: String,
    @DrawableRes icon: Int,
    labels: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit
): AlertDialog {
    var dialog: AlertDialog? = null
    val inflater = LayoutInflater.from(this)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val primary = MaterialColors.getColor(list, androidx.appcompat.R.attr.colorPrimary)

    labels.forEachIndexed { index, label ->
        val row = inflater.inflate(R.layout.item_dialog_choice, list, false)
        val selected = index == selectedIndex
        row.findViewById<TextView>(R.id.choiceLabel).text = label
        row.findViewById<ImageView>(R.id.choiceMark).setImageResource(
            if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
        )
        row.background = GradientDrawable().apply {
            cornerRadius = 16 * resources.displayMetrics.density
            setColor(if (selected) ColorUtils.setAlphaComponent(primary, 0x24) else Color.TRANSPARENT)
        }
        row.setOnClickListener {
            dialog?.dismiss()
            onPick(index)
        }
        list.addView(row)
    }

    dialog = showAppDialog(
        title = title, icon = icon,
        positiveText = null, negativeText = "Cancel",
        content = list
    )
    return dialog
}
