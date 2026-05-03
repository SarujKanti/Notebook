package com.skd.notebook.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        val name: TextView   = view.findViewById(R.id.txtFolderName)
        val icon: ImageView  = view.findViewById(R.id.imgFolderIcon)
        val accent: View     = view.findViewById(R.id.viewFolderAccent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FolderViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder, parent, false)
        )

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder  = getItem(position)
        val context = holder.itemView.context

        holder.name.text = folder.name

        // Resolve the folder's accent colour
        val accentColor = if (folder.color.isNotEmpty())
            runCatching { Color.parseColor(folder.color) }
                .getOrDefault(ContextCompat.getColor(context, R.color.colorPrimary))
        else
            ContextCompat.getColor(context, R.color.colorPrimary)

        // Apply colour to left stripe and folder icon tint
        holder.accent.setBackgroundColor(accentColor)
        holder.icon.imageTintList = ColorStateList.valueOf(accentColor)

        holder.itemView.setOnClickListener     { onClick(folder) }
        holder.itemView.setOnLongClickListener { onLongClick(folder); true }
    }
}
