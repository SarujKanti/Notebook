package com.skd.notebook.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private val onLongClick: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity) =
                oldItem == newItem
        }

        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNote)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val desc: TextView = view.findViewById(R.id.txtDesc)
        val timestamp: TextView = view.findViewById(R.id.txtTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note, parent, false)
        )

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)

        // Title
        if (note.title.isBlank()) {
            holder.title.visibility = View.GONE
        } else {
            holder.title.visibility = View.VISIBLE
            holder.title.text = note.title
        }

        // Description
        if (note.description.isBlank()) {
            holder.desc.visibility = View.GONE
        } else {
            holder.desc.visibility = View.VISIBLE
            holder.desc.text = note.description
        }

        // Timestamp
        holder.timestamp.text = DATE_FORMAT.format(Date(note.timestamp))

        // Card color
        val cardColor = if (note.color.isNotEmpty()) {
            runCatching { Color.parseColor(note.color) }.getOrDefault(Color.WHITE)
        } else {
            Color.WHITE
        }
        holder.card.setCardBackgroundColor(cardColor)

        // Adjust stroke: colored cards get no stroke; white cards get a subtle outline
        holder.card.strokeWidth = if (note.color.isEmpty()) 1 else 0

        // Long press → edit
        holder.itemView.setOnLongClickListener {
            onLongClick(note)
            true
        }
    }
}
