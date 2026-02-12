package com.skd.notebook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity

class NoteAdapter(
    private val onDelete: (NoteEntity) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val list = mutableListOf<NoteEntity>()

    fun submitList(notes: List<NoteEntity>) {
        list.clear()
        list.addAll(notes)
        notifyDataSetChanged()
    }

    inner class NoteViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val title: TextView = view.findViewById(R.id.txtTitle)
        val desc: TextView = view.findViewById(R.id.txtDesc)
        val delete: ImageView = view.findViewById(R.id.imgDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NoteViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note, parent, false)
        )

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = list[position]
        holder.title.text = note.title
        holder.desc.text = note.description
        holder.delete.setOnClickListener { onDelete(note) }
    }

    override fun getItemCount() = list.size
}
