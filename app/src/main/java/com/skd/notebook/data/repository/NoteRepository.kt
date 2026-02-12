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
        dao.insert(note)          // Local
        firebase.saveNote(note)   // Cloud
    }

    suspend fun delete(note: NoteEntity) {
        dao.delete(note)
        firebase.deleteNote(note.id)
    }

    suspend fun fetchNotesFromCloud() {
        val cloudNotes = firebase.getNotes()
        cloudNotes.forEach {
            dao.insert(it)   // Save cloud data into Room
        }
    }
}
