package com.skd.notebook.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.util.NoteColors

/**
 * Bottom-sheet editor for an existing note. Saves when closed, and again from [saveIfOpen]
 * (call it from onPause) so nothing is lost if the app is backgrounded with the sheet open.
 * A save only happens when the title, text or color really changed, because saving moves
 * the note to the top of its list.
 */
class NoteEditorSheet(
    private val activity: AppCompatActivity,
    private val viewModel: NoteViewModel
) {
    private var dialog: BottomSheetDialog? = null
    private var pendingSave: (() -> Unit)? = null

    fun show(note: NoteEntity) {
        val sheet = BottomSheetDialog(activity)
        val view  = activity.layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle    = view.findViewById<EditText>(R.id.etTitle)
        val etDesc     = view.findViewById<EditText>(R.id.etDesc)
        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
        val colorRow   = view.findViewById<LinearLayout>(R.id.colorPickerRow)

        var selectedColor = note.color
        etTitle.setText(note.title)
        etDesc.setText(note.description)
        dialogRoot.setBackgroundColor(NoteColors.background(activity, note.color))

        buildColorPicker(colorRow, selectedColor) { chosen ->
            selectedColor = chosen
            dialogRoot.setBackgroundColor(NoteColors.background(activity, chosen))
        }

        var lastSaved = Triple(note.title.trim(), note.description.trim(), selectedColor)
        val save: () -> Unit = {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            val current = Triple(title, desc, selectedColor)
            if ((title.isNotEmpty() || desc.isNotEmpty()) && current != lastSaved) {
                lastSaved = current
                viewModel.saveNote(note.copy(title = title, description = desc, color = selectedColor))
            }
        }

        btnClose.setOnClickListener { sheet.dismiss() }
        sheet.setOnDismissListener {
            save()
            dialog = null
            pendingSave = null
        }

        sheet.setContentView(view)
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
        dialog = sheet
        pendingSave = save
        sheet.show()
    }

    fun saveIfOpen() {
        if (dialog?.isShowing == true) pendingSave?.invoke()
    }

    private fun buildColorPicker(container: LinearLayout, currentColor: String, onPick: (String) -> Unit) {
        val sizePx   = activity.resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = activity.resources.getDimensionPixelSize(R.dimen.color_circle_margin)

        fun ring(hex: String, selected: String) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (hex.isEmpty()) Color.WHITE
                     else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE))
            val sel = hex == selected
            setStroke(if (sel) 4 else 2, if (sel) Color.DKGRAY else Color.LTGRAY)
        }

        NoteColors.PALETTE.forEachIndexed { index, hex ->
            container.addView(View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                    it.setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                background = ring(hex, currentColor)
                setOnClickListener {
                    onPick(hex)
                    for (i in 0 until container.childCount) {
                        container.getChildAt(i).background = ring(NoteColors.PALETTE[i], hex)
                    }
                }
            })
        }
    }
}
