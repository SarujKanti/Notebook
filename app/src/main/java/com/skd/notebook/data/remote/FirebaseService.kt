package com.skd.notebook.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteEntity
import kotlinx.coroutines.tasks.await

class FirebaseService {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun notesRef(): CollectionReference {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No authenticated user")
        return firestore.collection("users").document(uid).collection("notes")
    }

    private fun foldersRef(): CollectionReference {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No authenticated user")
        return firestore.collection("users").document(uid).collection("folders")
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

    suspend fun saveFolder(folder: FolderEntity) {
        foldersRef().document(folder.id).set(folder).await()
    }

    suspend fun deleteFolder(id: String) {
        foldersRef().document(id).delete().await()
    }

    suspend fun getFolders(): List<FolderEntity> {
        return foldersRef().get().await().documents.mapNotNull {
            it.toObject(FolderEntity::class.java)
        }
    }
}
