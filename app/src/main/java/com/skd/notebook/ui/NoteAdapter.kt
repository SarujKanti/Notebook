package com.skd.notebook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.skd.notebook.R
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.util.NoteColors
import com.skd.notebook.util.highlightMatches
import com.skd.notebook.util.snippetAroundMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onClick: (NoteEntity) -> Unit = {},
    private val onLongClick: (NoteEntity) -> Unit = {},
    private val onPinClick: (NoteEntity) -> Unit = {},
    private val showPin: Boolean = true,
    /** When set, each card shows a "more" (...) button that calls this. */
    private val onMoreClick: ((NoteEntity) -> Unit)? = null
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    /** While true, cards show a check circle instead of the pin and mark the selected ones. */
    var selectionMode: Boolean = false
        private set
    var selectedIds: Set<String> = emptySet()
        private set

    fun setSelection(mode: Boolean, ids: Set<String>) {
        selectionMode = mode
        selectedIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    /** Text to mark in titles and previews (search results). Empty = no highlighting. */
    var highlightQuery: String = ""

    /** Folders by id, so a note that lives in one can show which (see the folder chip). */
    private var folders: Map<String, FolderEntity> = emptyMap()

    fun setFolders(list: List<FolderEntity>) {
        folders = list.associateBy { it.id }
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(a: NoteEntity, b: NoteEntity) = a.id == b.id
            override fun areContentsTheSame(a: NoteEntity, b: NoteEntity) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        private const val FOLD_SIZE_DP = 18f // matches bg_pin_fold
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNote)
        val title: TextView        = view.findViewById(R.id.txtTitle)
        val desc: TextView         = view.findViewById(R.id.txtDesc)
        val timestamp: TextView    = view.findViewById(R.id.txtTimestamp)
        val btnPin: ImageButton    = view.findViewById(R.id.btnPin)
        val pinFold: View          = view.findViewById(R.id.pinFold)
        val folderChip: TextView   = view.findViewById(R.id.txtFolder)
        val btnMore: ImageButton   = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

    /** "In a folder" marker: a small pill with the folder's icon (in its color) and name. */
    private fun bindFolderChip(holder: NoteViewHolder, note: NoteEntity) {
        val folder = if (note.folderId.isNotEmpty()) folders[note.folderId] else null
        val chip = holder.folderChip
        if (folder == null) {
            chip.visibility = View.GONE
            return
        }
        val ctx = chip.context
        val dp = ctx.resources.displayMetrics.density
        val accent = runCatching { Color.parseColor(folder.color) }
            .getOrDefault(ContextCompat.getColor(ctx, R.color.colorPrimary))

        chip.text = folder.name
        chip.visibility = View.VISIBLE
        chip.background = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor(ColorUtils.setAlphaComponent(accent, 0x2E))
        }
        ContextCompat.getDrawable(ctx, R.drawable.ic_folder)?.mutate()?.let { icon ->
            icon.setTint(accent)
            val size = (14 * dp).toInt()
            icon.setBounds(0, 0, size, size)
            chip.setCompoundDrawablesRelative(icon, null, null, null)
        }
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        val folded = showPin && note.isPinned // pinned notes get the folded corner instead of an outline
        val ctx  = holder.itemView.context

        val highlightColor = ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, R.color.colorAccent), 0x99)
        holder.title.text       = highlightMatches(note.title, highlightQuery, highlightColor)
        holder.title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE

        holder.desc.text       = highlightMatches(snippetAroundMatch(note.description, highlightQuery), highlightQuery, highlightColor)
        holder.desc.visibility = if (note.description.isBlank()) View.GONE else View.VISIBLE

        holder.timestamp.text = DATE_FORMAT.format(Date(note.timestamp))
        bindFolderChip(holder, note)


        if (note.color.isNotEmpty()) {
            // Colored note: the note color is the whole card background
            val cardColor = NoteColors.background(ctx, note.color)
            holder.card.setCardBackgroundColor(cardColor)
            holder.card.strokeWidth = 0
        } else {
            // Default surface card (white in light, dark in dark); the hairline outline is dropped on folded cards
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.colorSurface)
            )
            holder.card.strokeWidth = if (folded) 0 else ctx.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.colorSurfaceVariant)
        }

        if (selectionMode) {
            val selected = note.id in selectedIds
            holder.btnPin.visibility = View.VISIBLE
            holder.btnPin.setImageResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle_outline)
            holder.btnPin.contentDescription = if (selected) "Selected" else "Not selected"
            holder.btnPin.setOnClickListener(null)
            holder.btnPin.isClickable = false
            if (selected) {
                holder.card.strokeWidth = ctx.resources.getDimensionPixelSize(R.dimen.card_stroke_width) * 3
                holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.colorPrimary)
            }
        } else if (showPin) {
            holder.btnPin.visibility = View.VISIBLE
            holder.btnPin.setImageResource(if (note.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
            holder.btnPin.contentDescription = ctx.getString(R.string.pin_note)
            holder.btnPin.setOnClickListener { onPinClick(note) }
        } else {
            holder.btnPin.visibility = View.GONE
        }

        // A folded note really has its top-right corner cut off (not painted over), so no card
        // edge or shadow is left showing around the missing corner. Others keep the round corner.
        val density = ctx.resources.displayMetrics.density
        holder.card.shapeAppearanceModel = holder.card.shapeAppearanceModel.toBuilder()
            .setTopRightCorner(
                if (folded) CornerFamily.CUT else CornerFamily.ROUNDED,
                if (folded) FOLD_SIZE_DP * density else ctx.resources.getDimension(R.dimen.card_corner_radius)
            )
            .build()
        holder.pinFold.visibility = if (folded) View.VISIBLE else View.GONE

        holder.btnMore.visibility = if (onMoreClick != null) View.VISIBLE else View.GONE
        holder.btnMore.setOnClickListener { onMoreClick?.invoke(note) }

        holder.itemView.setOnClickListener     { onClick(note) }
        holder.itemView.setOnLongClickListener { onLongClick(note); true }
    }
}

