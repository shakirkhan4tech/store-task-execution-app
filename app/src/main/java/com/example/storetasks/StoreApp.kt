package com.example.storetasks

import android.app.Application
import androidx.work.ExistingWorkPolicy
import com.example.storetasks.di.AppContainer
import com.example.storetasks.sync.SyncScheduler
import kotlinx.coroutines.launch

class StoreApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // If uploads gave up earlier (or the app was killed), re-arm them on launch.
        container.appScope.launch {
            if (container.repository.hasPendingSync()) {
                SyncScheduler.enqueue(this@StoreApp, ExistingWorkPolicy.KEEP)
            }
        }
    }
}
