package com.example.storetasks.data.remote

data class LoginRequest(val employeeId: String)

data class LoginResponse(val employeeId: String, val name: String)

/** status: "OPEN" or "COMPLETED" */
data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val dueAt: Long,
    val status: String,
)
