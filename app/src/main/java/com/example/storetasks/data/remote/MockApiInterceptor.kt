package com.example.storetasks.data.remote

import com.example.storetasks.util.NetworkMonitor
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections

/**
 * Fake backend. Answers every request locally so the app works without a server.
 *
 * It still behaves like a network: it fails with an IOException when the device is offline,
 * and it adds latency so loading states are visible.
 *
 * Demo employee IDs:
 *   0000 -> login fails (401)          2000 -> empty task list
 *   9999 -> task fetch fails (500)     anything else (3+ chars) -> 5 sample tasks
 *
 * To use a real backend: set AppContainer.USE_MOCK_API = false and update BASE_URL.
 */
class MockApiInterceptor(private val network: NetworkMonitor) : Interceptor {

    private val gson = Gson()

    // Server-side "database" of completed tasks (resets when the app process dies).
    private val completedTaskIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!network.isOnline()) throw IOException("No internet connection")
        try {
            Thread.sleep(LATENCY_MS)
        } catch (e: InterruptedException) {
            throw InterruptedIOException("Interrupted")
        }

        val path = request.url.encodedPath
        val tasksMatch = TASKS_PATH.matchEntire(path)
        val completeMatch = COMPLETE_PATH.matchEntire(path)

        return when {
            request.method == "POST" && path.endsWith("/login") -> login(request)
            request.method == "GET" && tasksMatch != null -> tasks(request, tasksMatch.groupValues[1])
            request.method == "POST" && completeMatch != null -> complete(request, completeMatch.groupValues[1])
            else -> respond(request, 404, errorJson("Not found"))
        }
    }

    private fun login(request: Request): Response {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val id = runCatching { gson.fromJson(buffer.readUtf8(), LoginRequest::class.java).employeeId }
            .getOrNull()
            .orEmpty()

        return when {
            id.isBlank() -> respond(request, 400, errorJson("Employee ID is required"))
            id == UNKNOWN_ID -> respond(request, 401, errorJson("Unknown employee ID"))
            else -> respond(request, 200, gson.toJson(LoginResponse(id, "Employee $id")))
        }
    }

    private fun tasks(request: Request, employeeId: String): Response = when (employeeId) {
        SERVER_ERROR_ID -> respond(request, 500, errorJson("Internal server error"))
        EMPTY_ID -> respond(request, 200, "[]")
        else -> respond(request, 200, gson.toJson(sampleTasks(employeeId)))
    }

    private fun complete(request: Request, taskId: String): Response {
        completedTaskIds += taskId
        return respond(request, 200, """{"ok":true}""")
    }

    private fun sampleTasks(employeeId: String): List<TaskDto> {
        val hour = 3_600_000L
        val now = System.currentTimeMillis()
        val base = now - now % hour // stable within the hour so the list doesn't reshuffle

        val templates = listOf(
            Triple("Restock cereal aisle", "Pull pallet B from the backroom and face every shelf. Photograph the shelf before and after.", "Aisle 4"),
            Triple("Update dairy price tags", "Replace tags that don't match this week's price sheet.", "Dairy cooler"),
            Triple("Build seasonal endcap", "Set up the fall display using the planogram in the manager's office.", "Front of store"),
            Triple("Clean spill zone at entrance", "Mop, dry, and put the wet-floor sign back in the closet.", "Main entrance"),
            Triple("Beverage planogram reset", "Move sodas to the new layout. Remove expired items you find.", "Aisle 9"),
        )

        return templates.mapIndexed { index, (title, description, location) ->
            val id = "T-$employeeId-${index + 1}"
            TaskDto(
                id = id,
                title = title,
                description = description,
                location = location,
                dueAt = base + (index + 1) * 2 * hour,
                status = if (id in completedTaskIds) "COMPLETED" else "OPEN",
            )
        }
    }

    private fun errorJson(message: String) = gson.toJson(mapOf("error" to message))

    private fun respond(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private companion object {
        const val LATENCY_MS = 700L
        const val UNKNOWN_ID = "0000"
        const val EMPTY_ID = "2000"
        const val SERVER_ERROR_ID = "9999"
        val TASKS_PATH = Regex("/v1/employees/([^/]+)/tasks")
        val COMPLETE_PATH = Regex("/v1/tasks/([^/]+)/complete")
    }
}
