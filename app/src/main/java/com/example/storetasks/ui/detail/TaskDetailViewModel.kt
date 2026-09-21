package com.example.storetasks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storetasks.data.TaskRepository
import com.example.storetasks.data.local.PhotoKind
import com.example.storetasks.data.local.TaskEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Ready(val task: TaskEntity) : DetailUiState
}

@OptIn(FlowPreview::class)
class TaskDetailViewModel(
    private val taskId: String,
    private val repository: TaskRepository,
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = repository.observeTask(taskId)
        .map { task -> toState(task) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val notesInput = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Autosave notes shortly after the user stops typing so nothing is lost.
        viewModelScope.launch {
            notesInput.debounce(400).collect { repository.saveNotes(taskId, it) }
        }
    }

    fun onNotesChanged(text: String) {
        notesInput.tryEmit(text)
    }

    fun newPhotoFile(kind: PhotoKind): File = repository.newPhotoFile(taskId, kind)

    fun onPhotoCaptured(kind: PhotoKind, file: File) {
        viewModelScope.launch { repository.attachPhoto(taskId, kind, file) }
    }

    fun complete(notes: String) {
        viewModelScope.launch {
            repository.completeTask(taskId, notes)
                .onSuccess { _messages.emit("Task completed. It will sync automatically.") }
                .onFailure { e -> _messages.emit(e.message ?: "Couldn't complete the task.") }
        }
    }

    private fun toState(task: TaskEntity?): DetailUiState =
        if (task == null) DetailUiState.NotFound else DetailUiState.Ready(task)
}
