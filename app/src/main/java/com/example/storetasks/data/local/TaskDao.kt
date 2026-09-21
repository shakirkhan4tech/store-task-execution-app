package com.example.storetasks.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {

    // ---- Reads ----

    @Query("SELECT * FROM tasks WHERE employeeId = :employeeId ORDER BY dueAt ASC")
    abstract fun observeTasks(employeeId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract fun observeTask(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE localState = 'PENDING_SYNC'")
    abstract suspend fun getPendingSync(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE localState = 'PENDING_SYNC'")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE employeeId = :employeeId")
    abstract suspend fun countFor(employeeId: String): Int

    // ---- Server -> local merge (never touches local work) ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(tasks: List<TaskEntity>)

    @Query(
        "UPDATE tasks SET title = :title, description = :description, " +
            "location = :location, dueAt = :dueAt WHERE id = :id"
    )
    abstract suspend fun updateServerFields(
        id: String, title: String, description: String, location: String, dueAt: Long,
    )

    @Query("UPDATE tasks SET localState = 'SYNCED' WHERE id = :id AND localState = 'NEW'")
    abstract suspend fun markSyncedIfNew(id: String)

    @Query(
        "DELETE FROM tasks WHERE employeeId = :employeeId AND localState = 'NEW' " +
            "AND id NOT IN (:keepIds)"
    )
    abstract suspend fun deleteStaleNew(employeeId: String, keepIds: List<String>)

    /**
     * Applies a fresh server list atomically. Tasks the employee has already started
     * or completed keep their local photos/notes/state; only server-owned fields update.
     */
    @Transaction
    open suspend fun applyServerTasks(employeeId: String, incoming: List<TaskEntity>) {
        insertIgnore(incoming)
        incoming.forEach { t ->
            updateServerFields(t.id, t.title, t.description, t.location, t.dueAt)
            if (t.localState == LocalState.SYNCED) markSyncedIfNew(t.id)
        }
        deleteStaleNew(employeeId, incoming.map { it.id })
    }

    // ---- Local work (guarded so finished tasks can't be edited) ----

    @Query(
        "UPDATE tasks SET notes = :notes, localState = 'IN_PROGRESS' " +
            "WHERE id = :id AND localState IN ('NEW', 'IN_PROGRESS')"
    )
    abstract suspend fun updateNotes(id: String, notes: String): Int

    @Query(
        "UPDATE tasks SET beforePhotoPath = :path, localState = 'IN_PROGRESS' " +
            "WHERE id = :id AND localState IN ('NEW', 'IN_PROGRESS')"
    )
    abstract suspend fun updateBeforePhoto(id: String, path: String): Int

    @Query(
        "UPDATE tasks SET afterPhotoPath = :path, localState = 'IN_PROGRESS' " +
            "WHERE id = :id AND localState IN ('NEW', 'IN_PROGRESS')"
    )
    abstract suspend fun updateAfterPhoto(id: String, path: String): Int

    /** Returns 0 if the task isn't editable or is missing a photo. */
    @Query(
        "UPDATE tasks SET notes = :notes, completedAt = :completedAt, localState = 'PENDING_SYNC' " +
            "WHERE id = :id AND localState IN ('NEW', 'IN_PROGRESS') " +
            "AND beforePhotoPath IS NOT NULL AND afterPhotoPath IS NOT NULL"
    )
    abstract suspend fun markPendingSync(id: String, notes: String, completedAt: Long): Int

    @Query("UPDATE tasks SET localState = 'SYNCED' WHERE id = :id AND localState = 'PENDING_SYNC'")
    abstract suspend fun markSynced(id: String)
}
