package com.skd.notebook.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.skd.notebook.data.local.NoteEntity
import kotlinx.coroutines.tasks.await

class FirebaseService {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Returns the Firestore collection scoped to the currently signed-in user:
     *   users/{uid}/notes
     * Throws if no user is authenticated (should never happen — MainActivity
     * redirects to Login on auth loss).
     */
    private fun notesRef(): CollectionReference {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No authenticated user")
        return firestore.collection("users").document(uid).collection("notes")
    }

    suspend fun saveNote(note: NoteEntity) {
        notesRef().document(note.id).set(note).await()
    }

    suspend fun deleteNote(id: String) {
        notesRef().document(id).delete().await()
    }

    suspend fun getNotes(): List<NoteEntity> {
        return notesRef().get().await().documents.mapNotNull {
            it.toObject(NoteEntity::class.java)
        }
    }
}
