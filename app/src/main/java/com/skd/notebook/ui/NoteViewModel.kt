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
import com.skd.notebook.util.escapeLike
import com.skd.notebook.util.NoteImages
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.repository.NoteRepository
import com.skd.notebook.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val pinnedNotes   = repo.pinnedNotes.asLiveData()
    val folders       = repo.folders.asLiveData()

    /** Any mutation that could change what the "Pinned Notes" home screen widget shows. */
    private fun refreshPinnedWidget() = WidgetUpdater.refreshPinnedWidget(getApplication())

    suspend fun getNoteById(id: String) = repo.getNoteById(id)

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
    fun searchNotes(query: String)       = repo.searchNotes(escapeLike(query)).asLiveData()

    // ─── Note operations ─────────────────────────────────────────────────────

    /**
     * Upserts a note (insert-or-replace by id). Used for both creating a new note
     * and editing an existing one — the note editor pre-generates an id up front
     * and calls this repeatedly as it auto-saves, so it must be safe to call more
     * than once with the same id (e.g. on dismiss, then again from onPause).
     */
    fun saveNote(note: NoteEntity) = viewModelScope.launch {
        repo.addNote(note.copy(timestamp = System.currentTimeMillis()))
        refreshPinnedWidget()
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch {
        repo.togglePin(note)
        refreshPinnedWidget()
    }

    /**
     * The user dragged notes into a new order: [shown] is the list as it now stands on screen.
     * The notes swap the places they already had among themselves (so notes that are not on screen,
     * such as those in other folders, keep their relative order); if some have no place yet, the
     * shown notes are simply numbered 1, 2, 3...
     */
    fun reorder(shown: List<NoteEntity>) = viewModelScope.launch {
        val places = shown.map { it.sortIndex }.sorted()
        val usable = places.toSet().size == places.size && (places.firstOrNull() ?: 1L) > 0L
        val targets = if (usable) places else shown.indices.map { it + 1L }
        val changed = shown.mapIndexedNotNull { i, note ->
            if (note.sortIndex != targets[i]) note.copy(sortIndex = targets[i]) else null
        }
        if (changed.isNotEmpty()) repo.reorder(changed)
    }

    fun moveToBin(note: NoteEntity)         = viewModelScope.launch { repo.moveToBin(note); refreshPinnedWidget() }
    fun restoreFromBin(note: NoteEntity)    = viewModelScope.launch { repo.restore(note); refreshPinnedWidget() }
    fun deletePermanently(note: NoteEntity) = viewModelScope.launch {
        repo.deletePermanently(note)
        deletePhotosOf(listOf(note))
        refreshPinnedWidget()
    }
    fun restoreAll(notes: List<NoteEntity>) = viewModelScope.launch {
        notes.forEach { repo.restore(it) }
        refreshPinnedWidget()
    }
    fun deleteAllPermanently(notes: List<NoteEntity>) = viewModelScope.launch {
        notes.forEach { repo.deletePermanently(it) }
        deletePhotosOf(notes)
        refreshPinnedWidget()
    }
    fun emptyBin() = viewModelScope.launch {
        val binned = db.noteDao().getBinNotesList()
        repo.emptyBin()
        deletePhotosOf(binned)
    }

    /** A note that is gone for good takes its photos with it (from this device and from the account). */
    private fun deletePhotosOf(notes: List<NoteEntity>) {
        NoteImages.delete(getApplication(), notes.flatMap { NoteImages.ids(it.imageIds) })
    }
    fun archive(note: NoteEntity)           = viewModelScope.launch { repo.archive(note); refreshPinnedWidget() }
    fun unarchive(note: NoteEntity)         = viewModelScope.launch { repo.unarchive(note); refreshPinnedWidget() }
    fun moveToFolder(note: NoteEntity, folderId: String) = viewModelScope.launch { repo.moveToFolder(note, folderId) }

    // ─── Folder operations ───────────────────────────────────────────────────

    fun createFolder(name: String, color: String = "") = viewModelScope.launch {
        repo.addFolder(FolderEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis(), color))
    }

    fun updateFolder(folder: FolderEntity) = viewModelScope.launch { repo.updateFolder(folder) }
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
                db.noteDao().insertAll(notes)
                isSyncing.postValue(false)   // first snapshot received — stop spinner
                refreshPinnedWidget()
            }
        }

        foldersListener = firebase.listenToFolders { folders ->
            viewModelScope.launch {
                db.folderDao().insertAll(folders)
            }
        }
    }

    /** Signing out closes the screen, which would cancel viewModelScope half-way, so this uses a scope of its own. */
    fun clearLocalData() {
        appScope.launch {
            repo.clearLocalData()
            NoteImages.clearLocal(getApplication())
            refreshPinnedWidget()
        }
    }

    private companion object {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCleared() {
        notesListener?.remove()
        foldersListener?.remove()
        super.onCleared()
    }
}
