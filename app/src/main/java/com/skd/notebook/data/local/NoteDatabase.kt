package com.skd.notebook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(entities = [NoteEntity::class, FolderEntity::class], version = 9)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * No physical schema change — v5 already has the isPinned column with the
         * right DEFAULT. This just re-syncs Room's identity hash after adding
         * @ColumnInfo(defaultValue) to the entity, which v5 was built without.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        /** Notes can have photos attached: a comma-separated list of their ids. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN imageIds TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Notes can be put in the user's own order by dragging: each keeps its place in sortIndex. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Notes and folders remember whether the cloud has confirmed them yet (needsSync). */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                // Until now nothing knew whether the cloud had a note, and a refused or missed save was forgotten:
                // flag everything already on the phone, so the first sync after updating sends it all.
                db.execSQL("UPDATE notes SET needsSync = 1")
                db.execSQL("UPDATE folders SET needsSync = 1")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, NoteDatabase::class.java, "note_db")
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
