package com.skd.notebook.ui

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.skd.notebook.R
import com.skd.notebook.util.FontStyles
import com.skd.notebook.util.dimenPx
import kotlin.math.min

/**
 * Shows the profile picture large, the way chat apps show a contact's photo: the screen dims, a square
 * card pops up with the name over the picture (a big initial on purple when there is none), and a row of
 * actions below. Tap outside the card to close it, tap the picture (or the full screen button) to see it
 * full screen, or tap Edit to change the profile.
 */
fun BaseActivity.showProfilePhoto(name: String, photo: Bitmap?, onEdit: () -> Unit) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val view = layoutInflater.inflate(R.layout.dialog_profile_photo, null)

    view.findViewById<TextView>(R.id.viewerName).text = name
    val picture = view.findViewById<ImageView>(R.id.viewerPhoto)
    val initial = view.findViewById<TextView>(R.id.viewerInitial)
    if (photo != null) {
        picture.setImageBitmap(photo)
        picture.visibility = View.VISIBLE
    } else {
        initial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        initial.visibility = View.VISIBLE
    }

    // A square card, as wide as the screen allows up to a comfortable size.
    val screenWidth = resources.displayMetrics.widthPixels
    val cardWidth = min(screenWidth - 2 * dimenPx(R.dimen.dp_36), dimenPx(R.dimen.dp_400))
    view.findViewById<View>(R.id.viewerCard).layoutParams.width = cardWidth
    view.findViewById<View>(R.id.viewerPhotoBox).layoutParams.height = cardWidth

    val fullScreen = {
        if (photo != null) {
            dialog.dismiss()
            showFullScreenPhoto(photo)
        }
    }
    view.findViewById<View>(R.id.viewerRoot).setOnClickListener { dialog.dismiss() }
    picture.setOnClickListener { fullScreen() }
    view.findViewById<View>(R.id.viewerFullScreen).apply {
        isEnabled = photo != null
        alpha = if (photo != null) 1f else 0.35f
        setOnClickListener { fullScreen() }
    }
    view.findViewById<View>(R.id.viewerEdit).setOnClickListener {
        dialog.dismiss()
        onEdit()
    }

    dialog.setContentView(view)
    dialog.window?.apply {
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setWindowAnimations(R.style.Animation_NoteBook_ProfileViewer)
    }
    dialog.applyGlassBackdrop() // the screen behind is blurred and dimmed like every other dialog
    dialog.show()
    FontStyles.applyToTree(dialog.window?.decorView)
}

/** The picture alone on a black screen; a tap closes it. */
private fun BaseActivity.showFullScreenPhoto(photo: Bitmap) {
    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val image = ImageView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = getString(R.string.profile_picture)
        setImageBitmap(photo)
        setOnClickListener { dialog.dismiss() }
    }
    dialog.setContentView(image)
    dialog.show()
}
