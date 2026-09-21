package com.example.storetasks.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storetasks.data.TaskRepository
import com.example.storetasks.data.local.TaskEntity
import com.example.storetasks.util.NetworkMonitor
import com.example.storetasks.util.UiError
import com.example.storetasks.util.toUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskListUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: UiError? = null,
    val isOnline: Boolean = true,
    val pendingSync: Int = 0,
)

private data class RefreshState(
    val isRefreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: UiError? = null,
)

class TaskListViewModel(
    private val repository: TaskRepository,
    private val network: NetworkMonitor,
) : ViewModel() {

    private val employee = repository.currentEmployee()
    private val employeeId = employee?.id.orEmpty()
    val employeeName: String = employee?.name.orEmpty()

    private val refreshState = MutableStateFlow(RefreshState())

    val uiState: StateFlow<TaskListUiState> = combine(
        repository.observeTasks(employeeId),
        refreshState,
        network.isOnlineFlow,
        repository.observePendingCount(),
    ) { tasks, refresh, online, pending ->
        TaskListUiState(
            tasks = tasks,
            isRefreshing = refresh.isRefreshing,
            loadedOnce = refresh.loadedOnce,
            error = refresh.error,
            isOnline = online,
            pendingSync = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState(isRefreshing = true))

    init {
        refresh()

        // Refresh automatically when connectivity comes back.
        viewModelScope.launch {
            var wasOnline = network.isOnline()
            network.isOnlineFlow.collect { online ->
                if (online && !wasOnline) refresh()
                wasOnline = online
            }
        }
    }

    fun refresh() {
        if (refreshState.value.isRefreshing) return
        viewModelScope.launch {
            refreshState.update { s -> s.copy(isRefreshing = true, error = null) }
            repository.refresh(employeeId)
                .onSuccess {
                    refreshState.update { s -> s.copy(isRefreshing = false, loadedOnce = true) }
                }
                .onFailure { e ->
                    refreshState.update { s -> s.copy(isRefreshing = false, error = e.toUiError()) }
                }
        }
    }

    fun logout() = repository.logout()
}
