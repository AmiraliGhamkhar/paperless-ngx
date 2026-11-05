package studio.vela.art.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import studio.vela.art.presentation.editor.EditorScreen
import studio.vela.art.presentation.gallery.GalleryScreen
import studio.vela.art.presentation.newcanvas.NewCanvasScreen
import studio.vela.art.presentation.settings.SettingsScreen
import studio.vela.art.ui.theme.VelaTheme

object Routes {
    const val GALLERY = "gallery"
    const val NEW_CANVAS = "new-canvas"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"

    fun editor(projectId: Long) = "$EDITOR/$projectId"
}

@Composable
fun VelaApp(container: AppContainer) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.Factory(container.settingsRepository),
    )
    val settings by appViewModel.settings.collectAsStateWithLifecycle()

    VelaTheme(settings = settings) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val colors = VelaTheme.colors

        Box(modifier = Modifier.fillMaxSize().background(colors.ground)) {
            NavHost(navController = navController, startDestination = Routes.GALLERY) {
                composable(Routes.GALLERY) {
                    GalleryScreen(
                        container = container,
                        onNewCanvas = { navController.navigate(Routes.NEW_CANVAS) },
                        onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    )
                }
                composable(Routes.NEW_CANVAS) {
                    NewCanvasScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onCreated = { projectId ->
                            navController.popBackStack()
                            navController.navigate(Routes.editor(projectId))
                        },
                    )
                }
                composable(
                    route = "${Routes.EDITOR}/{projectId}",
                    arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
                ) { entry ->
                    val projectId = entry.arguments?.getLong("projectId") ?: 0L
                    EditorScreen(
                        projectId = projectId,
                        container = container,
                        settings = settings,
                        onBack = { navController.popBackStack() },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        settings = settings,
                        onUpdate = { transform -> appViewModel.update(transform) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) { data ->
                Snackbar(snackbarData = data, containerColor = colors.surface2, contentColor = colors.textPrimary)
            }
        }
    }
}
