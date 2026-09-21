package com.example.storetasks.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storetasks.data.local.TaskEntity
import com.example.storetasks.ui.components.Banner
import com.example.storetasks.ui.components.CenteredMessage
import com.example.storetasks.ui.components.LoadingState
import com.example.storetasks.ui.components.StatusChip
import com.example.storetasks.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onOpenTask: (String) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    val hasTasks = state.tasks.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My tasks")
                        Text(viewModel.employeeName, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLoggedOut()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isRefreshing && hasTasks) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // Banners (only when there's a list underneath; empty screens explain themselves)
            if (hasTasks && !state.isOnline) {
                Banner("You're offline. Showing saved tasks.")
            } else if (hasTasks && error != null) {
                Banner(
                    text = "Couldn't refresh: ${error.message}",
                    container = MaterialTheme.colorScheme.errorContainer,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
            if (state.pendingSync > 0) {
                Banner(
                    text = "${state.pendingSync} completed task(s) will upload when you're online.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
            }

            when {
                !hasTasks && error != null -> {
                    val offline = error.isOffline || !state.isOnline
                    CenteredMessage(
                        icon = Icons.Default.Warning,
                        title = if (offline) "You're offline" else "Couldn't load tasks",
                        body = if (offline) {
                            "Connect to the internet to load your tasks."
                        } else {
                            error.message
                        },
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                    )
                }

                !hasTasks && !state.loadedOnce -> LoadingState()

                !hasTasks -> CenteredMessage(
                    icon = Icons.Default.Info,
                    title = "No tasks assigned",
                    body = "You're all caught up. Check back later.",
                    actionLabel = "Refresh",
                    onAction = viewModel::refresh,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCard(task, onClick = { onOpenTask(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(task.localState)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${task.location} · Due ${formatDateTime(task.dueAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
