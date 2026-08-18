package com.skd.notebook.ui

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.skd.notebook.R

class ActionItem(
    val label: String,
    @DrawableRes val icon: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Bottom-sheet action menu with icon + label rows — used in place of a plain
 * AlertDialog.setItems() list for long-press/overflow actions, matching the
 * rounded bottom-sheet look already used for the note and folder editors.
 */
fun Context.showActionSheet(title: String? = null, items: List<ActionItem>) {
    val dialog = BottomSheetDialog(this)
    val view   = LayoutInflater.from(this).inflate(R.layout.dialog_action_sheet, null)

    val tvTitle   = view.findViewById<TextView>(R.id.actionSheetTitle)
    val container = view.findViewById<LinearLayout>(R.id.actionSheetItems)

    if (!title.isNullOrEmpty()) {
        tvTitle.text = title
        tvTitle.visibility = View.VISIBLE
    }

    val destructiveColor = ContextCompat.getColor(this, R.color.color_red_700)

    items.forEach { item ->
        val row   = LayoutInflater.from(this).inflate(R.layout.item_action_sheet_row, container, false)
        val icon  = row.findViewById<ImageView>(R.id.actionRowIcon)
        val label = row.findViewById<TextView>(R.id.actionRowLabel)

        icon.setImageResource(item.icon)
        label.text = item.label
        if (item.destructive) {
            icon.imageTintList = ColorStateList.valueOf(destructiveColor)
            label.setTextColor(destructiveColor)
        }
        row.setOnClickListener {
            dialog.dismiss()
            item.onClick()
        }
        container.addView(row)
    }

    dialog.setContentView(view)
    dialog.show()
}

/** Colors the positive button red on a confirm dialog for a destructive action (delete, empty, etc). */
fun AlertDialog.tintPositiveButtonDestructive(context: Context) {
    getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(
        ContextCompat.getColor(context, R.color.color_red_700)
    )
}
