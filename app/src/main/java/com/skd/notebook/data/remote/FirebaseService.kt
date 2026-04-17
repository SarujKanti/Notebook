package com.skd.notebook.data.remote

import android.util.Log
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

    // ─── One-shot writes ─────────────────────────────────────────────────────

    suspend fun saveNote(note: NoteEntity) {
        notesRef().document(note.id).set(note).await()
    }

    suspend fun deleteNote(id: String) {
        notesRef().document(id).delete().await()
    }

    suspend fun saveFolder(folder: FolderEntity) {
        foldersRef().document(folder.id).set(folder).await()
    }

    suspend fun deleteFolder(id: String) {
        foldersRef().document(id).delete().await()
    }

    suspend fun getNotes(): List<NoteEntity> =
        notesRef().get().await().documents
            .mapNotNull { it.toObject(NoteEntity::class.java) }

    suspend fun getFolders(): List<FolderEntity> =
        foldersRef().get().await().documents
            .mapNotNull { it.toObject(FolderEntity::class.java) }

    // ─── Real-time listeners ─────────────────────────────────────────────────

    /**
     * Attaches a real-time Firestore listener for the current user's notes.
     *
     * IMPORTANT: the callback is ALWAYS called — even on error (with an empty list).
     * This ensures isSyncing is always cleared and the UI never gets stuck on a spinner.
     *
     * On PERMISSION_DENIED: check Firestore Security Rules in the Firebase Console.
     * Required rule:
     *   match /users/{userId}/{document=**} {
     *     allow read, write: if request.auth != null && request.auth.uid == userId;
     *   }
     */
    fun listenToNotes(onUpdate: (List<NoteEntity>) -> Unit): ListenerRegistration? {
        uid() ?: run {
            Log.w(TAG, "listenToNotes: no authenticated user — skipping")
            onUpdate(emptyList())
            return null
        }
        return notesRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "listenToNotes error: ${error.message} [code=${error.code}]" +
                        "\n>>> If code is PERMISSION_DENIED, update Firestore Security Rules in Firebase Console.", error)
                onUpdate(emptyList())   // ← always clear the spinner, never hang
                return@addSnapshotListener
            }
            if (snapshot == null) {
                Log.w(TAG, "listenToNotes: null snapshot")
                onUpdate(emptyList())
                return@addSnapshotListener
            }
            val notes = snapshot.documents.mapNotNull {
                it.toObject(NoteEntity::class.java)
            }
            Log.d(TAG, "listenToNotes: received ${notes.size} note(s) from Firestore")
            onUpdate(notes)
        }
    }

    /**
     * Same as listenToNotes but for folders.
     * The callback is always invoked so callers never hang waiting for it.
     */
    fun listenToFolders(onUpdate: (List<FolderEntity>) -> Unit): ListenerRegistration? {
        uid() ?: run {
            Log.w(TAG, "listenToFolders: no authenticated user — skipping")
            onUpdate(emptyList())
            return null
        }
        return foldersRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "listenToFolders error: ${error.message} [code=${error.code}]", error)
                onUpdate(emptyList())
                return@addSnapshotListener
            }
            if (snapshot == null) {
                onUpdate(emptyList())
                return@addSnapshotListener
            }
            val folders = snapshot.documents.mapNotNull {
                it.toObject(FolderEntity::class.java)
            }
            Log.d(TAG, "listenToFolders: received ${folders.size} folder(s) from Firestore")
            onUpdate(folders)
        }
    }

    companion object {
        private const val TAG = "FirebaseService"
    }
}
