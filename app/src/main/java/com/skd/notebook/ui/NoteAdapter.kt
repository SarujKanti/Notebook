package com.skd.notebook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.skd.notebook.R
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
    private val showPin: Boolean = true
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

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(a: NoteEntity, b: NoteEntity) = a.id == b.id
            override fun areContentsTheSame(a: NoteEntity, b: NoteEntity) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNote)
        val title: TextView        = view.findViewById(R.id.txtTitle)
        val desc: TextView         = view.findViewById(R.id.txtDesc)
        val timestamp: TextView    = view.findViewById(R.id.txtTimestamp)
        val btnPin: ImageButton    = view.findViewById(R.id.btnPin)
        val pinFold: View          = view.findViewById(R.id.pinFold)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

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

        holder.pinFold.visibility = if (folded) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener     { onClick(note) }
        holder.itemView.setOnLongClickListener { onLongClick(note); true }
    }
}

