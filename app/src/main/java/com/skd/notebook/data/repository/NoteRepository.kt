package com.skd.notebook.data.repository

import com.skd.notebook.data.local.FolderDao
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteDao
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.remote.SyncStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Notes and folders live in Room (this phone) and Firestore (the cloud).
 *
 * Every change is first saved on the phone and flagged `needsSync`; the flag is cleared only once the
 * cloud has confirmed it. A change the cloud did not take (no connection, refused, the app closed first)
 * therefore stays flagged and is sent again by [flushUnsynced], instead of being forgotten, and the cloud's
 * older copy never overwrites a flagged one. Failures are reported through [SyncStatus], not swallowed.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val firebase: FirebaseService,
    /** Called when a save could not be confirmed in time (usually: no connection), so a background upload can be queued. */
    private val onUploadDelayed: () -> Unit = {}
) {

    val activeNotes  = noteDao.getActiveNotes()
    val binNotes     = noteDao.getBinNotes()
    val archivedNotes= noteDao.getArchivedNotes()
    val pinnedNotes  = noteDao.getPinnedNotes()
    val folders      = folderDao.getAllFolders()

    /** How many notes and folders the cloud has not confirmed yet. */
    val unsyncedCount = combine(noteDao.unsyncedCount(), folderDao.unsyncedCount()) { notes, folders -> notes + folders }

    fun getFolderNotes(folderId: String) = noteDao.getNotesByFolder(folderId)
    fun searchNotes(query: String)      = noteDao.searchNotes(query)
    suspend fun getNoteById(id: String) = noteDao.getNoteById(id)

    // ─── Note CRUD ───────────────────────────────────────────────────────────

    suspend fun addNote(note: NoteEntity) = saveNote(note, newRow = true)

    suspend fun update(note: NoteEntity) = saveNote(note, newRow = false)

    /** Saves the places the user dragged notes to, without touching their edit times. */
    suspend fun reorder(notes: List<NoteEntity>) {
        val pending = notes.map { it.copy(needsSync = true) }
        noteDao.updateAll(pending)
        pushNotes(pending)
    }

    /** Soft-delete: moves note to Bin */
    suspend fun moveToBin(note: NoteEntity) =
        saveNote(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis(), isArchived = false), newRow = false)

    /** Restore from Bin back to active notes */
    suspend fun restore(note: NoteEntity) =
        saveNote(note.copy(isDeleted = false, deletedAt = 0L, isArchived = false), newRow = false)

    /** Permanently delete a note (used from Bin) */
    suspend fun deletePermanently(note: NoteEntity) {
        noteDao.delete(note)
        pushDeletion { firebase.deleteNote(note.id) }
    }

    /** Delete all bin notes permanently */
    suspend fun emptyBin() {
        // Collect IDs before wiping locally so we can delete from Firestore too
        val ids = noteDao.getBinNotesList().map { it.id }
        noteDao.emptyBin()
        pushDeletion { ids.forEach { firebase.deleteNote(it) } }
    }

    /** Toggle a note's pinned state */
    suspend fun togglePin(note: NoteEntity) = saveNote(note.copy(isPinned = !note.isPinned), newRow = false)

    /** Archive a note */
    suspend fun archive(note: NoteEntity) = saveNote(note.copy(isArchived = true, isDeleted = false), newRow = false)

    /** Unarchive a note back to active */
    suspend fun unarchive(note: NoteEntity) = saveNote(note.copy(isArchived = false), newRow = false)

    /** Move note to a folder (or remove from folder if folderId is empty) */
    suspend fun moveToFolder(note: NoteEntity, folderId: String) = saveNote(note.copy(folderId = folderId), newRow = false)

    // ─── Folder CRUD ─────────────────────────────────────────────────────────

    suspend fun addFolder(folder: FolderEntity) = saveFolder(folder)

    suspend fun updateFolder(folder: FolderEntity) = saveFolder(folder) // REPLACE strategy acts as upsert

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.delete(folder)
        pushDeletion { firebase.deleteFolder(folder.id) }
    }

    // ─── Cloud sync ──────────────────────────────────────────────────────────

    suspend fun fetchFromCloud() {
        try {
            firebase.getNotes().forEach { noteDao.insert(it) }
            firebase.getFolders().forEach { folderDao.insert(it) }
            SyncStatus.readOk()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncStatus.readFailed(e)
        }
    }

    /** Sends everything the cloud has not confirmed yet (left over from offline use, a failed save, or guest mode). */
    suspend fun flushUnsynced() {
        val notes = noteDao.getUnsynced()
        if (notes.isNotEmpty()) pushNotes(notes)
        val folders = folderDao.getUnsynced()
        if (folders.isNotEmpty()) pushFolders(folders)
    }

    fun isSignedIn() = firebase.isSignedIn()

    suspend fun unsyncedCountNow(): Int = noteDao.getUnsynced().size + folderDao.getUnsynced().size

    /** Ids the cloud's copy must not overwrite, because the phone has a newer one it has not sent yet. */
    suspend fun unsyncedNoteIds(): Set<String> = noteDao.getUnsyncedIds().toSet()

    suspend fun unsyncedFolderIds(): Set<String> = folderDao.getUnsyncedIds().toSet()

    /** A guest is signing in: everything on the phone now has to go to their account. */
    suspend fun markEverythingUnsynced() {
        noteDao.markAllUnsynced()
        folderDao.markAllUnsynced()
    }

    suspend fun clearLocalData() {
        noteDao.deleteAll()
        folderDao.deleteAll()
    }

    // ─── Saving: phone first, flagged, then the cloud ────────────────────────

    private suspend fun saveNote(note: NoteEntity, newRow: Boolean) {
        val pending = note.copy(needsSync = true)
        if (newRow) noteDao.insert(pending) else noteDao.update(pending)
        pushNotes(listOf(pending))
    }

    private suspend fun saveFolder(folder: FolderEntity) {
        val pending = folder.copy(needsSync = true)
        folderDao.insert(pending)
        pushFolders(listOf(pending))
    }

    /** Sends [notes]; the ones the cloud confirms stop being flagged. A failure is reported and they stay flagged. */
    private suspend fun pushNotes(notes: List<NoteEntity>) {
        if (!firebase.isSignedIn()) return // a guest has no cloud account: the notes simply wait, flagged
        try {
            // Without a connection the cloud never answers: stop waiting after a while and leave it to the background upload.
            val confirmed = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                if (notes.size == 1) firebase.saveNote(notes[0]) else firebase.saveNotes(notes)
                true
            } ?: false
            if (!confirmed) {
                onUploadDelayed()
                return
            }
            notes.forEach { sent ->
                val now = noteDao.getNoteById(sent.id)
                // Only unflag it if it has not changed again since it was sent.
                if (now != null && now == sent) noteDao.update(now.copy(needsSync = false))
            }
            SyncStatus.writeOk()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncStatus.writeFailed(e)
        }
    }

    private suspend fun pushFolders(folders: List<FolderEntity>) {
        if (!firebase.isSignedIn()) return
        try {
            val confirmed = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                if (folders.size == 1) firebase.saveFolder(folders[0]) else firebase.saveFolders(folders)
                true
            } ?: false
            if (!confirmed) {
                onUploadDelayed()
                return
            }
            // Folders only have a name, a color and a time, so "unchanged since sent" is checked by the row as it is now.
            folders.forEach { sent ->
                val stillSame = folderDao.getUnsynced().firstOrNull { it.id == sent.id } == sent
                if (stillSame) folderDao.update(sent.copy(needsSync = false))
            }
            SyncStatus.writeOk()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncStatus.writeFailed(e)
        }
    }

    private suspend fun pushDeletion(block: suspend () -> Unit) {
        if (!firebase.isSignedIn()) return
        try {
            block()
            SyncStatus.writeOk()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncStatus.writeFailed(e)
        }
    }

    private companion object {
        /** How long a save waits for the cloud to confirm before it is left to the background upload. */
        const val CONFIRM_TIMEOUT_MS = 10_000L
    }
}
