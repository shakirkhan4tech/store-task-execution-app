package com.example.storetasks.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a task stands on THIS device (independent of what the server says). */
enum class LocalState {
    NEW,           // assigned, nothing done yet
    IN_PROGRESS,   // draft saved locally (some photos and/or notes)
    PENDING_SYNC,  // marked complete, waiting to upload
    SYNCED,        // completion uploaded (or server already had it as completed)
}

enum class PhotoKind { BEFORE, AFTER }

@Entity(tableName = "tasks", indices = [Index("employeeId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val title: String,
    val description: String,
    val location: String,
    val dueAt: Long,
    val localState: LocalState = LocalState.NEW,
    val beforePhotoPath: String? = null,
    val afterPhotoPath: String? = null,
    val notes: String = "",
    val completedAt: Long? = null,
) {
    val isEditable: Boolean
        get() = localState == LocalState.NEW || localState == LocalState.IN_PROGRESS

    val canComplete: Boolean
        get() = isEditable && beforePhotoPath != null && afterPhotoPath != null
}
