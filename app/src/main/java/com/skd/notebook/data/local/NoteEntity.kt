package com.skd.notebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")

data class NoteEntity(
    @PrimaryKey
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var timestamp: Long = 0L
)
