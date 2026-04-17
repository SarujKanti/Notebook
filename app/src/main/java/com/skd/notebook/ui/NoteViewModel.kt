package com.skd.notebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.skd.notebook.data.local.FolderEntity
import com.skd.notebook.data.local.NoteDatabase
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.repository.NoteRepository
import kotlinx.coroutines.delay
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

    /**
     * True while we are waiting for the first Firestore snapshot.
     * MainActivity observes this to show a loading spinner instead of
     * the "No notes yet" empty state on a fresh install / first launch.
     */
    val isSyncing = MutableLiveData(true)

    fun getFolderNotes(folderId: String) = repo.getFolderNotes(folderId).asLiveData()
    fun searchNotes(query: String)       = repo.searchNotes(query).asLiveData()

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

        isSyncing.postValue(true)

        // Safety timeout: if Firestore never responds (no internet, rules blocked, etc.)
        // clear the spinner after 10 seconds so the UI doesn't hang forever.
        viewModelScope.launch {
            delay(10_000)
            if (isSyncing.value == true) {
                isSyncing.postValue(false)
            }
        }

        notesListener = firebase.listenToNotes { notes ->
            viewModelScope.launch {
                notes.forEach { db.noteDao().insert(it) }
                isSyncing.postValue(false)   // first snapshot received — stop spinner
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
