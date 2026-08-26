package com.skd.notebook.data.local

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Single source of truth for a note — stored in both Room (local) and
 * Firestore (cloud) under users/{uid}/notes/{id}.
 *
 * @IgnoreExtraProperties: Firestore will silently skip unknown fields instead
 * of throwing an exception when deserializing — prevents crashes if the cloud
 * document has fields that don't exist in the current app version.
 *
 * @Keep — Firestore serializes/deserializes this via runtime reflection on its
 * getters, which R8 will otherwise rename/strip in release builds, breaking cloud
 * sync with "No properties to serialize found" (see also proguard-rules.pro).
 */
@Keep
@IgnoreExtraProperties
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey var id: String = "",
    var title: String = "",
    var description: String = "",
    var timestamp: Long = 0L,
    var color: String = "",
    var isDeleted: Boolean = false,
    var deletedAt: Long = 0L,
    var isArchived: Boolean = false,
    var folderId: String = "",
    @ColumnInfo(defaultValue = "0")
    var isPinned: Boolean = false
)
