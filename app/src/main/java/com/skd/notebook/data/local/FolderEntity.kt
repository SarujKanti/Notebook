package com.skd.notebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Stored in both Room (local) and Firestore under users/{uid}/folders/{id}.
 *
 * @IgnoreExtraProperties prevents Firestore deserialization errors on field mismatches.
 */
@IgnoreExtraProperties
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val timestamp: Long = 0L
)
