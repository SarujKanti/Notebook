package com.skd.notebook.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.skd.notebook.R
import com.skd.notebook.util.FontStyles
import com.skd.notebook.util.NoteImage
import com.skd.notebook.util.NoteImages
import com.skd.notebook.util.dimenPx
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The page under the note editor's title: the note's text with photos placed between its lines.
 *
 * It is a list of text boxes with a photo between each pair ([texts] always has one more entry than
 * [images]). A photo can be dragged by its corner handle to any line of the note, and its height changed
 * with the handle on its bottom edge. [snapshot] turns it back into the note's text plus each photo's
 * place (the character it sits in front of, at a line start) and height, which is what gets saved.
 *
 * The top-right menu (voice typing, a photo from the gallery, a photo from the camera) is wired here too.
 */
class NoteBody(
    private val activity: BaseActivity,
    root: View,
    private val etTitle: EditText
) {
    /** The note as it should be saved: its text, and where its photos are. */
    data class Snapshot(val description: String, val images: List<NoteImage>)

    private val scroll = root.findViewById<ScrollView>(R.id.noteScroll)
    private val container = root.findViewById<LinearLayout>(R.id.bodyContainer)
    private val inflater: LayoutInflater = activity.layoutInflater

    private var texts = mutableListOf("")
    private var images = mutableListOf<NoteImage>()

    private val minPhotoHeight = activity.dimenPx(R.dimen.dp_80)
    private val maxPhotoHeight = activity.dimenPx(R.dimen.dp_600) + activity.dimenPx(R.dimen.dp_200)
    private val density = activity.resources.displayMetrics.density

    init {
        root.findViewById<View>(R.id.btnMenu).setOnClickListener { openMenu(it) }
        rebuild()
    }

    // ─── The note as saved ───────────────────────────────────────────────────

    /** Shows [description] with [saved] photos in their places. */
    fun load(description: String, saved: List<NoteImage>) {
        val sorted = saved.sortedBy { it.pos }
        val bounds = sorted.map { it.pos.coerceIn(0, description.length) }
        val newTexts = mutableListOf<String>()
        var previous = 0
        bounds.forEach { bound ->
            newTexts += description.substring(previous, bound).removeSuffix("\n")
            previous = bound
        }
        newTexts += description.substring(previous)
        texts = newTexts
        images = sorted.mapIndexed { i, image -> image.copy(pos = bounds[i]) }.toMutableList()
        rebuild()
    }

    /** What to save. Surrounding blank space is trimmed, and the photos' places move with it. */
    fun snapshot(): Snapshot {
        syncFromViews()
        val text = StringBuilder()
        val placed = mutableListOf<NoteImage>()
        texts.forEachIndexed { k, part ->
            text.append(part)
            if (k < images.size) {
                if (part.isNotEmpty()) text.append('\n')
                placed += images[k].copy(pos = text.length)
            }
        }
        val whole = text.toString()
        val leading = whole.length - whole.trimStart().length
        val trimmed = whole.trim()
        return Snapshot(trimmed, placed.map { it.copy(pos = (it.pos - leading).coerceIn(0, trimmed.length)) })
    }

    /** Puts the cursor at the end of the note's text. */
    fun focusEnd() {
        val last = textBox(texts.lastIndex)
        last.requestFocus()
        last.setSelection(last.text.length)
    }

    private fun textBox(k: Int) = container.getChildAt(2 * k) as EditText

    private fun syncFromViews() {
        for (k in texts.indices) texts[k] = textBox(k).text.toString()
    }

    // ─── Building the page ───────────────────────────────────────────────────

    private fun rebuild(focusBlock: Int = -1) {
        container.removeAllViews()
        for (k in texts.indices) {
            val box = inflater.inflate(R.layout.item_note_text, container, false) as EditText
            box.setText(texts[k])
            if (k == texts.lastIndex) box.minHeight = activity.dimenPx(R.dimen.dp_260) // an easy place to tap to keep typing
            if (images.isEmpty()) box.hint = activity.getString(R.string.hint_note)
            container.addView(box)
            if (k < images.size) container.addView(photoBlock(images[k]))
        }
        if (focusBlock in texts.indices) {
            val box = textBox(focusBlock)
            box.requestFocus()
            box.setSelection(0)
        }
    }

    private fun photoBlock(image: NoteImage): View {
        val block = inflater.inflate(R.layout.item_note_image_block, container, false)
        val height = if (image.heightDp > 0) image.heightDp else DEFAULT_HEIGHT_DP
        block.layoutParams.height = (height * density).roundToInt()

        val photo = block.findViewById<ImageView>(R.id.blockImage)
        // The block shimmers until the photo is ready (or turns out not to be available).
        val shimmer = block.findViewById<View>(R.id.blockSkeleton)
        val cached = NoteImages.cached(image.id, PHOTO_PX)
        if (cached != null) {
            photo.setImageBitmap(cached)
            shimmer.visibility = View.GONE
        } else {
            NoteImages.loadAsync(activity, image.id, PHOTO_PX) { bitmap ->
                bitmap?.let { photo.setImageBitmap(it) }
                shimmer.visibility = View.GONE
            }
        }
        photo.setOnClickListener { openFullScreen(image.id) }

        block.findViewById<View>(R.id.blockClose).setOnClickListener { removePhoto(image.id) }
        attachMove(block.findViewById(R.id.blockMove), block, image.id)
        attachResize(block.findViewById(R.id.blockResize), block, image.id)
        return block
    }

    // ─── Adding, removing, placing photos ────────────────────────────────────

    /** Where a new photo goes: after the line the cursor is on (block number, offset), or the top when the title has the cursor. */
    private fun cursorSpot(): Pair<Int, Int> {
        if (etTitle.hasFocus()) return 0 to 0
        for (k in texts.indices) {
            val box = textBox(k)
            if (box.hasFocus()) {
                val layout = box.layout ?: return k to box.text.length
                val line = layout.getLineForOffset(box.selectionStart.coerceAtLeast(0))
                return k to layout.getLineEnd(line)
            }
        }
        return texts.lastIndex to texts.last().length
    }

    private fun addPhoto(uri: Uri, spot: Pair<Int, Int>, deleteAfter: File?) {
        activity.lifecycleScope.launch {
            val id = NoteImages.import(activity, uri)
            deleteAfter?.delete() // the camera's temporary file
            if (id == null) {
                Snackbar.make(container, R.string.photo_add_failed, Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            syncFromViews()
            val block = spot.first.coerceIn(0, texts.lastIndex)
            insertAt(block, spot.second.coerceIn(0, texts[block].length), NoteImage(id))
            rebuild(focusBlock = block + 1)
            container.post { scroll.smoothScrollTo(0, max(0, container.getChildAt(2 * block + 1).top - activity.dimenPx(R.dimen.dp_80))) }
        }
    }

    /** Splits text block [k] at [offset] and puts [image] in the gap. */
    private fun insertAt(k: Int, offset: Int, image: NoteImage) {
        val whole = texts[k]
        texts[k] = whole.substring(0, offset).removeSuffix("\n")
        texts.add(k + 1, whole.substring(offset))
        images.add(k, image)
    }

    /** Joins the two text blocks around photo [i] into one (the photo is taken out). Returns the new text's offset shift for the second block. */
    private fun mergeAround(i: Int): Int {
        val before = texts[i]
        val after = texts[i + 1]
        val shift = if (before.isEmpty()) 0 else if (after.isEmpty()) before.length else before.length + 1
        texts[i] = if (before.isEmpty()) after else if (after.isEmpty()) before else before + "\n" + after
        texts.removeAt(i + 1)
        images.removeAt(i)
        return shift
    }

    private fun removePhoto(id: String) {
        syncFromViews()
        val i = images.indexOfFirst { it.id == id }
        if (i < 0) return
        mergeAround(i)
        NoteImages.delete(activity, listOf(id))
        rebuild()
    }

    // ─── Dragging a photo to a new place ─────────────────────────────────────

    private var dragBlock: View? = null
    private var dragRawY = 0f
    private var dragTarget: Pair<Int, Int>? = null
    private val dropLine = ColorDrawable()

    private val dragTick = object : Runnable {
        override fun run() {
            val block = dragBlock ?: return
            // Near the top or bottom of the page the page scrolls by itself, so a photo can be taken far.
            val where = IntArray(2)
            scroll.getLocationOnScreen(where)
            val edge = activity.dimenPx(R.dimen.dp_72)
            val step = activity.dimenPx(R.dimen.dp_10)
            if (dragRawY < where[1] + edge) scroll.scrollBy(0, -step)
            else if (dragRawY > where[1] + scroll.height - edge) scroll.scrollBy(0, step)
            followFinger(block)
            block.postOnAnimation(this)
        }
    }

    /** The finger's height inside [container], which stays right while the page scrolls. */
    private fun fingerY(): Float {
        val where = IntArray(2)
        container.getLocationOnScreen(where)
        return dragRawY - where[1]
    }

    private fun followFinger(block: View) {
        block.translationY = fingerY() - (block.top + block.height / 2f)
        val index = images.indexOfFirst { it.id == block.tag }
        dragTarget = dropTarget(fingerY(), index)
        showDropLine(dragTarget)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachMove(handle: View, block: View, id: String) {
        block.tag = id
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    syncFromViews()
                    dragBlock = block
                    dragRawY = event.rawY
                    block.translationZ = activity.dimenPx(R.dimen.dp_12).toFloat()
                    block.alpha = 0.9f
                    scroll.requestDisallowInterceptTouchEvent(true)
                    block.postOnAnimation(dragTick)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    dragRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val target = dragTarget
                    block.removeCallbacks(dragTick)
                    dragBlock = null
                    dragTarget = null
                    showDropLine(null)
                    block.translationY = 0f
                    block.translationZ = 0f
                    block.alpha = 1f
                    if (event.actionMasked == MotionEvent.ACTION_UP && target != null) movePhoto(id, target)
                    true
                }
                else -> false
            }
        }
    }

    /** Takes photo [id] out and puts it in front of line [spot] (block number, offset), keeping the text as it is. */
    private fun movePhoto(id: String, spot: Pair<Int, Int>) {
        val i = images.indexOfFirst { it.id == id }
        if (i < 0) return
        val image = images[i]
        var (block, offset) = spot
        val beforeLength = texts[i].length
        val shift = mergeAround(i)
        when {
            // The two texts around the photo are now one, with a line break joining them.
            block == i -> if (offset >= beforeLength) offset += shift - beforeLength
            block == i + 1 -> { block = i; offset += shift }
            block > i + 1 -> block -= 1
        }
        block = block.coerceIn(0, texts.lastIndex)
        insertAt(block, offset.coerceIn(0, texts[block].length), image)
        rebuild()
    }

    /**
     * Which line of the note [y] (in [container]) is over: the block number and offset a dropped photo
     * would go in front of. Null when the photo is over its own place.
     */
    private fun dropTarget(y: Float, dragged: Int): Pair<Int, Int>? {
        val at = y.toInt()
        for (k in texts.indices) {
            val box = textBox(k)
            if (at in box.top..box.bottom) return k to offsetAt(box, at)
            if (k < images.size) {
                val block = container.getChildAt(2 * k + 1)
                val lp = block.layoutParams as ViewGroup.MarginLayoutParams
                if (at in (block.top - lp.topMargin)..(block.bottom + lp.bottomMargin)) {
                    if (k == dragged) return null
                    return if (at < (block.top + block.bottom) / 2) k to texts[k].length else (k + 1) to 0
                }
            }
        }
        return if (at < 0) 0 to 0 else texts.lastIndex to texts.last().length
    }

    /** The start of the line [y] is on, or its end when [y] is in the lower half of the line. */
    private fun offsetAt(box: EditText, y: Int): Int {
        val layout = box.layout ?: return 0
        val inside = (y - box.top - box.totalPaddingTop).coerceIn(0, max(layout.height - 1, 0))
        val line = layout.getLineForVertical(inside)
        val middle = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
        return if (inside > middle) layout.getLineEnd(line) else layout.getLineStart(line)
    }

    /** A thin purple line showing where the dragged photo will land (null hides it). */
    private fun showDropLine(spot: Pair<Int, Int>?) {
        container.overlay.remove(dropLine)
        if (spot == null) return
        val (k, offset) = spot
        val box = textBox(k)
        val layout = box.layout ?: return
        val text = box.text
        val base = box.top + box.totalPaddingTop
        val y = if (text.isNotEmpty() && offset >= text.length && !text.endsWith("\n")) {
            base + layout.getLineBottom(layout.lineCount - 1)
        } else {
            base + layout.getLineTop(layout.getLineForOffset(offset.coerceAtMost(text.length)))
        }
        val half = activity.dimenPx(R.dimen.dp_2)
        val side = activity.dimenPx(R.dimen.dp_20)
        dropLine.color = MaterialColors.getColor(container, androidx.appcompat.R.attr.colorPrimary)
        dropLine.setBounds(side, y - half, container.width - side, y + half)
        container.overlay.add(dropLine)
    }

    // ─── Changing a photo's height ───────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachResize(handle: View, block: View, id: String) {
        var startY = 0f
        var startHeight = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = block.height
                    scroll.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    block.layoutParams.height = (startHeight + event.rawY - startY).roundToInt()
                        .coerceIn(minPhotoHeight, maxPhotoHeight)
                    block.requestLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val i = images.indexOfFirst { it.id == id }
                    if (i >= 0) images[i] = images[i].copy(heightDp = (block.height / density).roundToInt())
                    true
                }
                else -> false
            }
        }
    }

    // ─── The menu on the top-right button ────────────────────────────────────

    private fun openMenu(anchor: View) {
        // Decided now: once the menu is open the text boxes lose their cursor.
        syncFromViews()
        val speakInto = focusedText()
        val spot = cursorSpot()

        // The card has a margin so its shadow has room; that margin only counts inside a parent.
        val menu = FrameLayout(activity)
        inflater.inflate(R.layout.popup_note_menu, menu, true)
        val popup = PopupWindow(menu, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.animationStyle = R.style.Animation_NoteBook_PopupMenu

        menu.findViewById<View>(R.id.menuVoice).setOnClickListener {
            popup.dismiss()
            activity.dictateInto(speakInto)
        }
        menu.findViewById<View>(R.id.menuGallery).setOnClickListener {
            popup.dismiss()
            activity.pickPhoto { uri -> addPhoto(uri, spot, deleteAfter = null) }
        }
        menu.findViewById<View>(R.id.menuCamera).setOnClickListener {
            popup.dismiss()
            activity.takePhoto(anchor) { uri -> addPhoto(uri, spot, deleteAfter = uri.path?.let { File(it) }) }
        }

        FontStyles.applyToTree(menu)
        popup.showAsDropDown(anchor, activity.dimenPx(R.dimen.dp_4), 0, Gravity.END)
    }

    /** The text box that has the cursor: the title, a block of the note, or the end of the note if none. */
    private fun focusedText(): EditText {
        if (etTitle.hasFocus()) return etTitle
        return texts.indices.map { textBox(it) }.firstOrNull { it.hasFocus() } ?: textBox(texts.lastIndex)
    }

    /** The photo on a black screen; a tap closes it. */
    private fun openFullScreen(id: String) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = ImageView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = activity.getString(R.string.note_photo)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(view)
        dialog.show()
        NoteImages.loadAsync(activity, id, FULL_PX) { bitmap -> bitmap?.let { view.setImageBitmap(it) } }
    }

    private companion object {
        const val DEFAULT_HEIGHT_DP = 200
        const val PHOTO_PX = 1280
        const val FULL_PX = 1600
    }
}
