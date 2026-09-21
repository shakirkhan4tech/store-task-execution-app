package com.example.storetasks.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface TaskApi {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("employees/{employeeId}/tasks")
    suspend fun getTasks(@Path("employeeId") employeeId: String): List<TaskDto>

    @Multipart
    @POST("tasks/{taskId}/complete")
    suspend fun completeTask(
        @Path("taskId") taskId: String,
        @Part("notes") notes: RequestBody,
        @Part("completedAt") completedAt: RequestBody,
        @Part before: MultipartBody.Part,
        @Part after: MultipartBody.Part,
    )
}
