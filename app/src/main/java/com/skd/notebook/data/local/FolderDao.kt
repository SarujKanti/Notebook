package com.skd.notebook.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY timestamp DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
