package com.skd.notebook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.skd.notebook.R
import com.skd.notebook.data.local.FolderEntity

class FolderAdapter(
    private val onClick: (FolderEntity) -> Unit,
    private val onLongClick: (FolderEntity) -> Unit
) : ListAdapter<FolderEntity, FolderAdapter.FolderViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FolderEntity>() {
            override fun areItemsTheSame(a: FolderEntity, b: FolderEntity) = a.id == b.id
            override fun areContentsTheSame(a: FolderEntity, b: FolderEntity) = a == b
        }
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtFolderName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FolderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false))

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.name.text = folder.name
        holder.itemView.setOnClickListener { onClick(folder) }
        holder.itemView.setOnLongClickListener { onLongClick(folder); true }
    }
}
