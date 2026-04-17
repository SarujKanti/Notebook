package com.skd.notebook.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    /** Active notes: not deleted, not archived, no folder */
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 AND folderId = '' ORDER BY timestamp DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    /** Bin: soft-deleted notes */
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getBinNotes(): Flow<List<NoteEntity>>

    /** Archive: archived and not deleted */
    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    /** Notes inside a specific folder, not deleted/archived */
    @Query("SELECT * FROM notes WHERE folderId = :folderId AND isDeleted = 0 AND isArchived = 0 ORDER BY timestamp DESC")
    fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyBin()
}
