package com.example.storetasks.data

import android.content.Context
import com.example.storetasks.data.local.LocalState
import com.example.storetasks.data.local.PhotoKind
import com.example.storetasks.data.local.PhotoStorage
import com.example.storetasks.data.local.TaskDao
import com.example.storetasks.data.local.TaskEntity
import com.example.storetasks.data.remote.LoginRequest
import com.example.storetasks.data.remote.TaskApi
import com.example.storetasks.data.remote.TaskDto
import com.example.storetasks.sync.SyncScheduler
import com.example.storetasks.util.safeCall
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class SyncOutcome { DONE, RETRY }

/** Single source of truth: the UI only ever reads Room; the network only feeds Room. */
class TaskRepository(
    private val appContext: Context,
    private val api: TaskApi,
    private val dao: TaskDao,
    private val session: SessionStore,
    private val photos: PhotoStorage,
) {

    // ---------- Session ----------

    fun currentEmployee(): Employee? = session.current()

    fun logout() = session.clear() // local drafts and pending uploads are kept

    suspend fun login(employeeId: String): Result<Employee> = safeCall {
        val employee = try {
            val response = api.login(LoginRequest(employeeId))
            Employee(response.employeeId, response.name)
        } catch (e: IOException) {
            // Offline: let returning employees in if this device already has their tasks.
            if (dao.countFor(employeeId) > 0) Employee(employeeId, "Employee $employeeId") else throw e
        }
        session.save(employee)
        employee
    }

    // ---------- Reads ----------

    fun observeTasks(employeeId: String): Flow<List<TaskEntity>> = dao.observeTasks(employeeId)

    fun observeTask(taskId: String): Flow<TaskEntity?> = dao.observeTask(taskId)

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun hasPendingSync(): Boolean = dao.getPendingSync().isNotEmpty()

    // ---------- Server -> local ----------

    suspend fun refresh(employeeId: String): Result<Unit> = safeCall {
        val dtos = api.getTasks(employeeId)
        dao.applyServerTasks(employeeId, dtos.map { it.toEntity(employeeId) })
    }

    // ---------- Local work ----------

    suspend fun saveNotes(taskId: String, notes: String) {
        dao.updateNotes(taskId, notes)
    }

    fun newPhotoFile(taskId: String, kind: PhotoKind): File = photos.newFile(taskId, kind)

    suspend fun attachPhoto(taskId: String, kind: PhotoKind, file: File) {
        val previous = dao.getTask(taskId)
        val path = file.absolutePath
        val rows = when (kind) {
            PhotoKind.BEFORE -> dao.updateBeforePhoto(taskId, path)
            PhotoKind.AFTER -> dao.updateAfterPhoto(taskId, path)
        }
        if (rows == 0) { // task no longer editable
            photos.delete(path)
            return
        }
        val oldPath = when (kind) {
            PhotoKind.BEFORE -> previous?.beforePhotoPath
            PhotoKind.AFTER -> previous?.afterPhotoPath
        }
        if (oldPath != null && oldPath != path) photos.delete(oldPath)
    }

    /** Works offline: marks the task complete locally and queues the upload. */
    suspend fun completeTask(taskId: String, notes: String): Result<Unit> {
        val rows = dao.markPendingSync(taskId, notes, System.currentTimeMillis())
        if (rows == 0) {
            return Result.failure(IllegalStateException("Add both Before and After photos first."))
        }
        SyncScheduler.enqueue(appContext)
        return Result.success(Unit)
    }

    // ---------- Local -> server (called by SyncWorker) ----------

    suspend fun syncPending(): SyncOutcome {
        var outcome = SyncOutcome.DONE
        for (task in dao.getPendingSync()) {
            try {
                upload(task)
                dao.markSynced(task.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return SyncOutcome.RETRY // offline or timeout: stop, let WorkManager back off
            } catch (e: Exception) {
                outcome = SyncOutcome.RETRY // e.g. HTTP 5xx: keep trying the remaining tasks
            }
        }
        return outcome
    }

    private suspend fun upload(task: TaskEntity) {
        val text = "text/plain".toMediaType()
        val jpeg = "image/jpeg".toMediaType()
        val before = File(requireNotNull(task.beforePhotoPath))
        val after = File(requireNotNull(task.afterPhotoPath))

        api.completeTask(
            taskId = task.id,
            notes = task.notes.toRequestBody(text),
            completedAt = (task.completedAt ?: System.currentTimeMillis()).toString().toRequestBody(text),
            before = MultipartBody.Part.createFormData("before", before.name, before.asRequestBody(jpeg)),
            after = MultipartBody.Part.createFormData("after", after.name, after.asRequestBody(jpeg)),
        )
    }
}

private fun TaskDto.toEntity(employeeId: String) = TaskEntity(
    id = id,
    employeeId = employeeId,
    title = title,
    description = description,
    location = location,
    dueAt = dueAt,
    localState = if (status == "COMPLETED") LocalState.SYNCED else LocalState.NEW,
)
