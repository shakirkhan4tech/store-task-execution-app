package com.example.storetasks.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.storetasks.data.local.LocalState
import com.example.storetasks.data.local.PhotoKind
import com.example.storetasks.data.local.TaskEntity
import com.example.storetasks.ui.components.CenteredMessage
import com.example.storetasks.ui.components.LoadingState
import com.example.storetasks.ui.components.StatusChip
import com.example.storetasks.util.formatDateTime
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(viewModel: TaskDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            DetailUiState.NotFound -> CenteredMessage(
                icon = Icons.Default.Warning,
                title = "Task not found",
                body = "This task is no longer available on this device.",
                modifier = Modifier.padding(padding),
                actionLabel = "Go back",
                onAction = onBack,
            )
            is DetailUiState.Ready -> TaskDetailContent(current.task, viewModel, padding)
        }
    }
}

@Composable
private fun TaskDetailContent(
    task: TaskEntity,
    viewModel: TaskDetailViewModel,
    padding: PaddingValues,
) {
    val context = LocalContext.current

    // Typed text lives here (so the cursor never jumps); the ViewModel autosaves it to Room.
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }

    // Remember which photo slot the camera is filling, across rotation / process death.
    var pendingKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val kind = pendingKind
        val path = pendingPath
        pendingKind = null
        pendingPath = null
        if (kind != null && path != null) {
            val file = File(path)
            if (saved) viewModel.onPhotoCaptured(PhotoKind.valueOf(kind), file) else file.delete()
        }
    }

    fun capture(kind: PhotoKind) {
        val file = viewModel.newPhotoFile(kind)
        pendingKind = kind.name
        pendingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.headlineSmall)
            StatusChip(task.localState)
            Text(
                "${task.location} · Due ${formatDateTime(task.dueAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(task.description, style = MaterialTheme.typography.bodyLarge)
        }

        when (task.localState) {
            LocalState.IN_PROGRESS -> Hint("Draft saved on this device. You can finish it later.")
            LocalState.PENDING_SYNC -> Hint("Completed. It will upload automatically when you're back online.")
            LocalState.SYNCED -> Hint("Completed and synced.")
            LocalState.NEW -> Unit
        }

        PhotoCard(
            label = "Before photo",
            path = task.beforePhotoPath,
            canEdit = task.isEditable,
            onCapture = { capture(PhotoKind.BEFORE) },
        )
        PhotoCard(
            label = "After photo",
            path = task.afterPhotoPath,
            canEdit = task.isEditable,
            onCapture = { capture(PhotoKind.AFTER) },
        )

        OutlinedTextField(
            value = notes,
            onValueChange = {
                notes = it
                viewModel.onNotesChanged(it)
            },
            label = { Text("Notes (optional)") },
            readOnly = !task.isEditable,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        if (task.isEditable) {
            Button(
                onClick = { viewModel.complete(notes) },
                enabled = task.canComplete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mark as completed")
            }
            if (!task.canComplete) {
                Text(
                    "Take both a Before and an After photo to complete this task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PhotoCard(label: String, path: String?, canEdit: Boolean, onCapture: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (path != null) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("No photo yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canEdit) {
                OutlinedButton(onClick = onCapture) {
                    Text(if (path == null) "Take photo" else "Retake")
                }
            }
        }
    }
}
