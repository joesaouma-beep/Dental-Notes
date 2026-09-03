package com.dentalstudio.notes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, EditEventEntity::class, RuleEntity::class, StyleProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun editEventDao(): EditEventDao
    abstract fun ruleDao(): RuleDao
    abstract fun styleProfileDao(): StyleProfileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dental-notes.db",
            ).build().also { instance = it }
        }
    }
}
