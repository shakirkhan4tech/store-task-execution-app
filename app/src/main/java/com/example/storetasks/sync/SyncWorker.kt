package com.example.storetasks.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.storetasks.StoreApp
import com.example.storetasks.data.SyncOutcome

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as StoreApp).container.repository
        return when (repository.syncPending()) {
            SyncOutcome.DONE -> Result.success()
            SyncOutcome.RETRY ->
                if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
