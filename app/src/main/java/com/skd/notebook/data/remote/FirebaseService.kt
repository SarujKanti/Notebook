package com.skd.notebook.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
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

    /** The profile picture: one small JPEG kept in the user's own area, so it follows the account to any device. */
    private fun profilePhotoRef() =
        firestore.collection("users")
            .document(uid() ?: throw IllegalStateException("No authenticated user"))
            .collection("profile")
            .document("photo")

    suspend fun saveProfilePhoto(jpeg: ByteArray) {
        profilePhotoRef().set(mapOf("data" to Blob.fromBytes(jpeg), "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun getProfilePhoto(): ByteArray? =
        profilePhotoRef().get().await().getBlob("data")?.toBytes()

    suspend fun deleteProfilePhoto() {
        profilePhotoRef().delete().await()
    }

    /** Photos attached to notes: one small JPEG per document, kept apart from the notes so the notes list stays light. */
    private fun noteImagesRef(): CollectionReference =
        firestore.collection("users")
            .document(uid() ?: throw IllegalStateException("No authenticated user"))
            .collection("noteImages")

    suspend fun saveNoteImage(id: String, jpeg: ByteArray) {
        noteImagesRef().document(id).set(mapOf("data" to Blob.fromBytes(jpeg), "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun getNoteImage(id: String): ByteArray? =
        noteImagesRef().document(id).get().await().getBlob("data")?.toBytes()

    suspend fun deleteNoteImage(id: String) {
        noteImagesRef().document(id).delete().await()
    }

    // ─── One-shot writes ─────────────────────────────────────────────────────

    suspend fun saveNote(note: NoteEntity) {
        notesRef().document(note.id).set(note).await()
    }

    /**
     * Saves many notes as one write (in batches of up to 400, Firestore's limit is 500). Saved one by one,
     * each save sent back a snapshot with the notes not yet saved still as they were, which put their old
     * values back on the phone.
     */
    suspend fun saveNotes(notes: List<NoteEntity>) {
        val col = notesRef()
        notes.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.set(col.document(it.id), it) }
            batch.commit().await()
        }
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
