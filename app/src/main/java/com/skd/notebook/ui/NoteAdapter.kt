package com.skd.notebook.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onClick: (NoteEntity) -> Unit = {},
    private val onLongClick: (NoteEntity) -> Unit = {}
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(a: NoteEntity, b: NoteEntity) = a.id == b.id
            override fun areContentsTheSame(a: NoteEntity, b: NoteEntity) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNote)
        val accent: View           = view.findViewById(R.id.viewAccent)
        val title: TextView        = view.findViewById(R.id.txtTitle)
        val desc: TextView         = view.findViewById(R.id.txtDesc)
        val timestamp: TextView    = view.findViewById(R.id.txtTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        val ctx  = holder.itemView.context

        holder.title.text       = note.title
        holder.title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE

        holder.desc.text       = note.description
        holder.desc.visibility = if (note.description.isBlank()) View.GONE else View.VISIBLE

        holder.timestamp.text = DATE_FORMAT.format(Date(note.timestamp))

        if (note.color.isNotEmpty()) {
            // Colored note: use note color as card background, show accent stripe
            val cardColor = runCatching { Color.parseColor(note.color) }.getOrDefault(Color.WHITE)
            holder.card.setCardBackgroundColor(cardColor)
            holder.card.strokeWidth = 0
            // Derive a slightly darker accent from the card color
            holder.accent.setBackgroundColor(darken(cardColor, 0.75f))
            holder.accent.visibility = View.VISIBLE
        } else {
            // Default white card: show primary color accent stripe for visual structure
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(ctx, android.R.color.white)
            )
            holder.card.strokeWidth = ctx.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.colorSurfaceVariant)
            holder.accent.setBackgroundColor(
                ContextCompat.getColor(ctx, R.color.colorPrimaryLight)
            )
            holder.accent.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener     { onClick(note) }
        holder.itemView.setOnLongClickListener { onLongClick(note); true }
    }

    /** Darkens an ARGB color by the given factor (0.0 = black, 1.0 = no change). */
    private fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color)   * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
