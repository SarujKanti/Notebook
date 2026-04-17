package com.skd.notebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val timestamp: Long = 0L
)
