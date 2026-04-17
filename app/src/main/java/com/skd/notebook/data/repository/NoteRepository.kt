package com.skd.notebook.data.repository

import com.skd.notebook.data.local.FolderDao
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteDao
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService

class NoteRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val firebase: FirebaseService
) {

    val activeNotes  = noteDao.getActiveNotes()
    val binNotes     = noteDao.getBinNotes()
    val archivedNotes= noteDao.getArchivedNotes()
    val folders      = folderDao.getAllFolders()

    fun getFolderNotes(folderId: String) = noteDao.getNotesByFolder(folderId)
    fun searchNotes(query: String)      = noteDao.searchNotes(query)

    // ─── Note CRUD ───────────────────────────────────────────────────────────

    suspend fun addNote(note: NoteEntity) {
        noteDao.insert(note)
        trySync { firebase.saveNote(note) }
    }

    suspend fun update(note: NoteEntity) {
        noteDao.update(note)
        trySync { firebase.saveNote(note) }
    }

    /** Soft-delete: moves note to Bin */
    suspend fun moveToBin(note: NoteEntity) {
        val updated = note.copy(isDeleted = true, deletedAt = System.currentTimeMillis(), isArchived = false)
        noteDao.update(updated)
        trySync { firebase.saveNote(updated) }
    }

    /** Restore from Bin back to active notes */
    suspend fun restore(note: NoteEntity) {
        val updated = note.copy(isDeleted = false, deletedAt = 0L, isArchived = false)
        noteDao.update(updated)
        trySync { firebase.saveNote(updated) }
    }

    /** Permanently delete a note (used from Bin) */
    suspend fun deletePermanently(note: NoteEntity) {
        noteDao.delete(note)
        trySync { firebase.deleteNote(note.id) }
    }

    /** Delete all bin notes permanently */
    suspend fun emptyBin() {
        // Collect IDs before wiping locally so we can delete from Firestore too
        val ids = noteDao.getBinNotesList().map { it.id }
        noteDao.emptyBin()
        trySync { ids.forEach { firebase.deleteNote(it) } }
    }

    /** Archive a note */
    suspend fun archive(note: NoteEntity) {
        val updated = note.copy(isArchived = true, isDeleted = false)
        noteDao.update(updated)
        trySync { firebase.saveNote(updated) }
    }

    /** Unarchive a note back to active */
    suspend fun unarchive(note: NoteEntity) {
        val updated = note.copy(isArchived = false)
        noteDao.update(updated)
        trySync { firebase.saveNote(updated) }
    }

    /** Move note to a folder (or remove from folder if folderId is empty) */
    suspend fun moveToFolder(note: NoteEntity, folderId: String) {
        val updated = note.copy(folderId = folderId)
        noteDao.update(updated)
        trySync { firebase.saveNote(updated) }
    }

    // ─── Folder CRUD ─────────────────────────────────────────────────────────

    suspend fun addFolder(folder: FolderEntity) {
        folderDao.insert(folder)
        trySync { firebase.saveFolder(folder) }
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.delete(folder)
        trySync { firebase.deleteFolder(folder.id) }
    }

    // ─── Cloud sync ──────────────────────────────────────────────────────────

    suspend fun fetchFromCloud() {
        trySync {
            firebase.getNotes().forEach { noteDao.insert(it) }
            firebase.getFolders().forEach { folderDao.insert(it) }
        }
    }

    suspend fun clearLocalData() {
        noteDao.deleteAll()
        folderDao.deleteAll()
    }

    private suspend fun trySync(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { }
    }
}
