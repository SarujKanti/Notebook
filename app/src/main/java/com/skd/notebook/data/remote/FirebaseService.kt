package com.skd.notebook.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteEntity
import kotlinx.coroutines.tasks.await

class FirebaseService {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid

    private fun notesRef(): CollectionReference =
        firestore.collection("users")
            .document(uid() ?: throw IllegalStateException("No authenticated user"))
            .collection("notes")

    private fun foldersRef(): CollectionReference =
        firestore.collection("users")
            .document(uid() ?: throw IllegalStateException("No authenticated user"))
            .collection("folders")

    // ─── One-shot writes / reads ─────────────────────────────────────────────

    suspend fun saveNote(note: NoteEntity)    { notesRef().document(note.id).set(note).await() }
    suspend fun deleteNote(id: String)        { notesRef().document(id).delete().await() }
    suspend fun saveFolder(folder: FolderEntity) { foldersRef().document(folder.id).set(folder).await() }
    suspend fun deleteFolder(id: String)      { foldersRef().document(id).delete().await() }

    suspend fun getNotes(): List<NoteEntity> =
        notesRef().get().await().documents.mapNotNull { it.toObject(NoteEntity::class.java) }

    suspend fun getFolders(): List<FolderEntity> =
        foldersRef().get().await().documents.mapNotNull { it.toObject(FolderEntity::class.java) }

    // ─── Real-time listeners ─────────────────────────────────────────────────

    /**
     * Attaches a Firestore snapshot listener for all notes of the current user.
     * Fires immediately with the current data set, then again on every remote change.
     * This makes notes appear instantly on any device the user signs into.
     * Returns null if the user is not authenticated (safe to call then discard).
     */
    fun listenToNotes(onUpdate: (List<NoteEntity>) -> Unit): ListenerRegistration? {
        uid() ?: return null
        return notesRef().addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            onUpdate(snapshot.documents.mapNotNull { it.toObject(NoteEntity::class.java) })
        }
    }

    /**
     * Same as listenToNotes but for folders.
     */
    fun listenToFolders(onUpdate: (List<FolderEntity>) -> Unit): ListenerRegistration? {
        uid() ?: return null
        return foldersRef().addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            onUpdate(snapshot.documents.mapNotNull { it.toObject(FolderEntity::class.java) })
        }
    }
}
