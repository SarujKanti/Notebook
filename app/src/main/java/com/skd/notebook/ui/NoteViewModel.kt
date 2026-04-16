package com.skd.notebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.skd.notebook.data.local.NoteDatabase
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.repository.NoteRepository
import kotlinx.coroutines.launch
import java.util.UUID

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = NoteDatabase.getDatabase(application).noteDao()
    private val repo = NoteRepository(dao, FirebaseService())

    val notes = repo.notes.asLiveData()

    fun addNote(title: String, desc: String, color: String = "") {
        viewModelScope.launch {
            val note = NoteEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = desc,
                timestamp = System.currentTimeMillis(),
                color = color
            )
            repo.addNote(note)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repo.update(note.copy(timestamp = System.currentTimeMillis()))
        }
    }

    /** Restores a deleted note (used for swipe-undo). Preserves the original id/timestamp. */
    fun restoreNote(note: NoteEntity) {
        viewModelScope.launch {
            repo.addNote(note)
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch {
            repo.delete(note)
        }
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            repo.fetchNotesFromCloud()
        }
    }

    /** Called on sign-out — wipes local cache before navigating to Login. */
    fun clearLocalNotes() {
        viewModelScope.launch {
            repo.clearLocalNotes()
        }
    }
}
