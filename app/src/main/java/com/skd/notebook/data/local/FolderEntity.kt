package com.skd.notebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Stored in both Room (local) and Firestore under users/{uid}/folders/{id}.
 *
 * @IgnoreExtraProperties prevents Firestore deserialization errors on field mismatches.
 * [color] hex string (e.g. "#F44336"). Empty = use app primary colour.
 */
@IgnoreExtraProperties
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val timestamp: Long = 0L,
    val color: String = ""          // hex e.g. "#4CAF50" — empty means default primary
)
