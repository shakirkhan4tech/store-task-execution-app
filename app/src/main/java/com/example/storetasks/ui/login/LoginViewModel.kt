package com.example.storetasks.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storetasks.data.TaskRepository
import com.example.storetasks.util.toUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val employeeId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdChange(value: String) {
        _uiState.update { it.copy(employeeId = value.trim().take(12), error = null) }
    }

    fun login() {
        val id = _uiState.value.employeeId
        if (_uiState.value.isLoading) return
        if (id.length < 3) {
            _uiState.update { it.copy(error = "Enter your employee ID (at least 3 characters).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.login(id)
                .onSuccess { _uiState.update { s -> s.copy(isLoading = false, loggedIn = true) } }
                .onFailure { e ->
                    val error = e.toUiError()
                    val message = if (error.isOffline) {
                        "You're offline. Connect to the internet to log in for the first time."
                    } else {
                        error.message
                    }
                    _uiState.update { s -> s.copy(isLoading = false, error = message) }
                }
        }
    }
}
