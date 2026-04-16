package com.skd.notebook.data.repository

import com.skd.notebook.data.local.NoteDao
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService

class NoteRepository(
    private val dao: NoteDao,
    private val firebase: FirebaseService
) {

    val notes = dao.getAllNotes()

    suspend fun addNote(note: NoteEntity) {
        dao.insert(note)
        try {
            firebase.saveNote(note)
        } catch (_: Exception) {
            // Cloud sync failed; note is safely stored locally
        }
    }

    suspend fun update(note: NoteEntity) {
        dao.update(note)
        try {
            firebase.saveNote(note)   // Firestore set() overwrites — works as upsert
        } catch (_: Exception) {
            // Cloud sync failed; update saved locally
        }
    }

    suspend fun delete(note: NoteEntity) {
        dao.delete(note)
        try {
            firebase.deleteNote(note.id)
        } catch (_: Exception) {
            // Cloud delete failed; note removed locally
        }
    }

    suspend fun fetchNotesFromCloud() {
        try {
            val cloudNotes = firebase.getNotes()
            cloudNotes.forEach { dao.insert(it) }
        } catch (_: Exception) {
            // Cloud unavailable; local data is used as fallback
        }
    }

    /** Clears the local Room cache on sign-out so the next user starts fresh. */
    suspend fun clearLocalNotes() {
        dao.deleteAll()
    }
}
