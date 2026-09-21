package com.example.storetasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.storetasks.di.AppContainer
import com.example.storetasks.ui.detail.TaskDetailScreen
import com.example.storetasks.ui.detail.TaskDetailViewModel
import com.example.storetasks.ui.login.LoginScreen
import com.example.storetasks.ui.login.LoginViewModel
import com.example.storetasks.ui.tasks.TaskListScreen
import com.example.storetasks.ui.tasks.TaskListViewModel

private const val LOGIN = "login"
private const val TASKS = "tasks"
private const val DETAIL = "task"

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val repository = container.repository
    val startDestination = remember { if (repository.currentEmployee() != null) TASKS else LOGIN }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(LOGIN) {
            val vm: LoginViewModel = viewModel(factory = vmFactory { LoginViewModel(repository) })
            LoginScreen(
                viewModel = vm,
                onLoggedIn = {
                    navController.navigate(TASKS) { popUpTo(LOGIN) { inclusive = true } }
                },
            )
        }

        composable(TASKS) {
            val vm: TaskListViewModel = viewModel(
                factory = vmFactory { TaskListViewModel(repository, container.networkMonitor) }
            )
            TaskListScreen(
                viewModel = vm,
                onOpenTask = { taskId -> navController.navigate("$DETAIL/$taskId") },
                onLoggedOut = {
                    navController.navigate(LOGIN) { popUpTo(TASKS) { inclusive = true } }
                },
            )
        }

        composable(
            route = "$DETAIL/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty()
            val vm: TaskDetailViewModel = viewModel(
                factory = vmFactory { TaskDetailViewModel(taskId, repository) }
            )
            TaskDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
