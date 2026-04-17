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
    private val onClick: (NoteEntity) -> Unit = {},
    private val onLongClick: (NoteEntity) -> Unit = {}
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(a: NoteEntity, b: NoteEntity) = a.id == b.id
            override fun areContentsTheSame(a: NoteEntity, b: NoteEntity) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNote)
        val title: TextView        = view.findViewById(R.id.txtTitle)
        val desc: TextView         = view.findViewById(R.id.txtDesc)
        val timestamp: TextView    = view.findViewById(R.id.txtTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)

        holder.title.text       = note.title
        holder.title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE

        holder.desc.text       = note.description
        holder.desc.visibility = if (note.description.isBlank()) View.GONE else View.VISIBLE

        holder.timestamp.text = DATE_FORMAT.format(Date(note.timestamp))

        val cardColor = if (note.color.isNotEmpty())
            runCatching { Color.parseColor(note.color) }.getOrDefault(Color.WHITE)
        else Color.WHITE
        holder.card.setCardBackgroundColor(cardColor)
        holder.card.strokeWidth = if (note.color.isEmpty()) 1 else 0

        holder.itemView.setOnClickListener     { onClick(note) }
        holder.itemView.setOnLongClickListener { onLongClick(note); true }
    }
}
