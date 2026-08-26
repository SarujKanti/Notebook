package com.skd.notebook.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Stored in both Room (local) and Firestore under users/{uid}/folders/{id}.
 *
 * @IgnoreExtraProperties prevents Firestore deserialization errors on field mismatches.
 * [color] hex string (e.g. "#F44336"). Empty = use app primary colour.
 *
 * @Keep — Firestore serializes/deserializes this via runtime reflection on its
 * getters, which R8 will otherwise rename/strip in release builds, breaking cloud
 * sync with "No properties to serialize found" (see also proguard-rules.pro).
 */
@Keep
@IgnoreExtraProperties
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val timestamp: Long = 0L,
    val color: String = ""          // hex e.g. "#4CAF50" — empty means default primary
)
