package com.skd.notebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteDatabase
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.repository.NoteRepository
import kotlinx.coroutines.launch
import java.util.UUID

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val db      = NoteDatabase.getDatabase(application)
    private val firebase = FirebaseService()
    private val repo    = NoteRepository(db.noteDao(), db.folderDao(), firebase)

    val activeNotes   = repo.activeNotes.asLiveData()
    val binNotes      = repo.binNotes.asLiveData()
    val archivedNotes = repo.archivedNotes.asLiveData()
    val folders       = repo.folders.asLiveData()

    // Real-time Firestore listener registrations
    private var notesListener: ListenerRegistration?   = null
    private var foldersListener: ListenerRegistration? = null

    fun getFolderNotes(folderId: String) = repo.getFolderNotes(folderId).asLiveData()

    // ─── Note operations ─────────────────────────────────────────────────────

    fun addNote(title: String, desc: String, color: String = "", folderId: String = "") {
        viewModelScope.launch {
            repo.addNote(NoteEntity(
                id          = UUID.randomUUID().toString(),
                title       = title,
                description = desc,
                timestamp   = System.currentTimeMillis(),
                color       = color,
                folderId    = folderId
            ))
        }
    }

    fun updateNote(note: NoteEntity) = viewModelScope.launch {
        repo.update(note.copy(timestamp = System.currentTimeMillis()))
    }

    fun moveToBin(note: NoteEntity)         = viewModelScope.launch { repo.moveToBin(note) }
    fun restoreFromBin(note: NoteEntity)    = viewModelScope.launch { repo.restore(note) }
    fun deletePermanently(note: NoteEntity) = viewModelScope.launch { repo.deletePermanently(note) }
    fun emptyBin()                          = viewModelScope.launch { repo.emptyBin() }
    fun archive(note: NoteEntity)           = viewModelScope.launch { repo.archive(note) }
    fun unarchive(note: NoteEntity)         = viewModelScope.launch { repo.unarchive(note) }
    fun moveToFolder(note: NoteEntity, folderId: String) = viewModelScope.launch { repo.moveToFolder(note, folderId) }

    // ─── Folder operations ───────────────────────────────────────────────────

    fun createFolder(name: String) = viewModelScope.launch {
        repo.addFolder(FolderEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis()))
    }

    fun deleteFolder(folder: FolderEntity) = viewModelScope.launch { repo.deleteFolder(folder) }

    // ─── Sync / session ──────────────────────────────────────────────────────

    /**
     * Starts real-time Firestore listeners. Notes and folders from the cloud
     * are written into Room whenever they change remotely — making the app
     * automatically show the correct data on any device the user signs into.
     */
    fun startRealtimeSync() {
        // Remove any existing listeners before (re)attaching
        notesListener?.remove()
        foldersListener?.remove()

        notesListener = firebase.listenToNotes { notes ->
            viewModelScope.launch {
                notes.forEach { db.noteDao().insert(it) }
            }
        }

        foldersListener = firebase.listenToFolders { folders ->
            viewModelScope.launch {
                folders.forEach { db.folderDao().insert(it) }
            }
        }
    }

    fun clearLocalData() = viewModelScope.launch { repo.clearLocalData() }

    override fun onCleared() {
        notesListener?.remove()
        foldersListener?.remove()
        super.onCleared()
    }
}
