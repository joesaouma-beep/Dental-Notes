package com.dentalstudio.notes.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.di.AppContainer
import com.dentalstudio.notes.ui.adapt.AdaptScreen
import com.dentalstudio.notes.ui.adapt.AdaptViewModel
import com.dentalstudio.notes.ui.home.DictateFab
import com.dentalstudio.notes.ui.home.HomeScreen
import com.dentalstudio.notes.ui.home.HomeViewModel
import com.dentalstudio.notes.ui.note.NoteScreen
import com.dentalstudio.notes.ui.note.NoteViewModel
import com.dentalstudio.notes.ui.record.RecordScreen
import com.dentalstudio.notes.ui.record.RecordViewModel
import com.dentalstudio.notes.ui.settings.SettingsScreen
import com.dentalstudio.notes.ui.settings.SettingsViewModel

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.dentalstudio.notes.data.prefs.AppSettings()
    )

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = homeFactory(container))
            AppScaffold(
                currentRoute = Routes.HOME,
                onNavigate = { navController.navigateTopLevel(it) },
                floatingActionButton = { DictateFab { navController.navigate(Routes.RECORD) } },
            ) { padding ->
                HomeScreen(
                    viewModel = viewModel,
                    padding = padding,
                    practiceName = settings.practiceName,
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                    onOpenAdapt = { navController.navigateTopLevel(Routes.ADAPT) },
                )
            }
        }

        composable(Routes.ADAPT) {
            val viewModel: AdaptViewModel = viewModel(factory = adaptFactory(container))
            AppScaffold(
                currentRoute = Routes.ADAPT,
                onNavigate = { navController.navigateTopLevel(it) },
            ) { padding ->
                AdaptScreen(viewModel = viewModel, padding = padding)
            }
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
            AppScaffold(
                currentRoute = Routes.SETTINGS,
                onNavigate = { navController.navigateTopLevel(it) },
            ) { padding ->
                SettingsScreen(viewModel = viewModel, padding = padding)
            }
        }

        composable(Routes.RECORD) {
            val viewModel: RecordViewModel = viewModel(factory = recordFactory(container))
            RecordScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onNoteReady = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.note(id))
                },
            )
        }

        composable(
            route = Routes.NOTE,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: 0L
            val viewModel: NoteViewModel = viewModel(factory = noteFactory(container, noteId))
            NoteScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}

/** Keeps a single entry per top-level destination on the back stack. */
private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
