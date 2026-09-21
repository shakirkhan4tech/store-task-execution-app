package com.example.storetasks.di

import android.content.Context
import androidx.room.Room
import com.example.storetasks.data.SessionStore
import com.example.storetasks.data.TaskRepository
import com.example.storetasks.data.local.AppDatabase
import com.example.storetasks.data.local.PhotoStorage
import com.example.storetasks.data.remote.MockApiInterceptor
import com.example.storetasks.data.remote.TaskApi
import com.example.storetasks.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Manual dependency injection: one place that wires everything together. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val networkMonitor = NetworkMonitor(appContext)

    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "store_tasks.db").build()

    private val httpClient = OkHttpClient.Builder().apply {
        if (USE_MOCK_API) addInterceptor(MockApiInterceptor(networkMonitor))
    }.build()

    private val api: TaskApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TaskApi::class.java)

    val repository = TaskRepository(
        appContext = appContext,
        api = api,
        dao = database.taskDao(),
        session = SessionStore(appContext),
        photos = PhotoStorage(appContext),
    )

    companion object {
        const val USE_MOCK_API = true
        const val BASE_URL = "https://api.example-store.com/v1/"
    }
}
