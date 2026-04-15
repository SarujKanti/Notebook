package com.skd.notebook.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.skd.notebook.data.local.NoteEntity
import kotlinx.coroutines.tasks.await

class FirebaseService {

    private val firestore = FirebaseFirestore.getInstance()
    private val notesRef = firestore.collection("notes")

    suspend fun saveNote(note: NoteEntity) {
        notesRef.document(note.id).set(note).await()
    }

    suspend fun deleteNote(id: String) {
        notesRef.document(id).delete().await()
    }

    suspend fun getNotes(): List<NoteEntity> {
        val snapshot = notesRef.get().await()
        return snapshot.documents.mapNotNull {
            it.toObject(NoteEntity::class.java)
        }
    }
}
