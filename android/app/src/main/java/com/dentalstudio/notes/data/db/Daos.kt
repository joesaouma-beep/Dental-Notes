package com.dentalstudio.notes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    /** Approved notes used as worked examples in the next prompt. */
    @Query("SELECT * FROM notes WHERE edited = 1 AND templateId = :templateId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentEditedForTemplate(templateId: String, limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE edited = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentEdited(limit: Int): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE createdAt >= :since")
    fun countSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

@Dao
interface EditEventDao {
    @Insert
    suspend fun insert(event: EditEventEntity): Long

    @Query("SELECT * FROM edit_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EditEventEntity>>

    @Query("SELECT COUNT(*) FROM edit_events")
    fun count(): Flow<Int>

    @Query("DELETE FROM edit_events")
    suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY occurrences DESC, lastSeenAt DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE type = :type AND scope = :scope AND pattern = :pattern AND replacement = :replacement LIMIT 1")
    suspend fun find(type: String, scope: String, pattern: String, replacement: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: RuleEntity): Long

    @Query("UPDATE rules SET occurrences = occurrences + 1, lastSeenAt = :now, enabled = 1 WHERE id = :id")
    suspend fun reinforce(id: Long, now: Long)

    @Query("UPDATE rules SET contradictions = contradictions + 1 WHERE id = :id")
    suspend fun contradict(id: Long)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /**
     * Retires preferences the clinician has argued with more often than not, so
     * a habit that was really a one-off stops steering future notes.
     */
    @Query("UPDATE rules SET enabled = 0 WHERE contradictions >= 3 AND CAST(occurrences AS REAL) / (occurrences + contradictions) < 0.4")
    suspend fun retireWeakRules()

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clear()
}

@Dao
interface StyleProfileDao {
    @Query("SELECT * FROM style_profiles WHERE scope = :scope LIMIT 1")
    suspend fun byScope(scope: String): StyleProfileEntity?

    @Query("SELECT * FROM style_profiles")
    fun observeAll(): Flow<List<StyleProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: StyleProfileEntity)

    @Query("DELETE FROM style_profiles")
    suspend fun clear()
}
