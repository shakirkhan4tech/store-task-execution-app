package com.example.storetasks.data

import android.content.Context

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun current(): Employee? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return Employee(id, prefs.getString(KEY_NAME, null) ?: id)
    }

    fun save(employee: Employee) {
        prefs.edit().putString(KEY_ID, employee.id).putString(KEY_NAME, employee.name).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ID = "employee_id"
        const val KEY_NAME = "employee_name"
    }
}
