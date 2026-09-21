package com.example.storetasks.util

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Like runCatching, but never swallows coroutine cancellation. */
inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

data class UiError(val message: String, val isOffline: Boolean = false)

fun Throwable.toUiError(): UiError = when (this) {
    is HttpException -> when (code()) {
        401, 403, 404 -> UiError("Employee ID not recognised.")
        in 500..599 -> UiError("Server error (${code()}). Please try again.")
        else -> UiError("Request failed (${code()}).")
    }
    is IOException -> UiError("You're offline or the server can't be reached.", isOffline = true)
    else -> UiError(message ?: "Something went wrong.")
}
