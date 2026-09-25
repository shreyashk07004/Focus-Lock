package com.focuslock.app.ui.nav

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.focuslock.app.ui.add.AddAppScreen
import com.focuslock.app.ui.dashboard.DashboardScreen
import com.focuslock.app.ui.dashboard.DashboardViewModel
import com.focuslock.app.ui.edit.EditAppScreen
import com.focuslock.app.ui.permissions.PermissionsScreen

private const val ANIM_MS = 280

@Composable
fun FocusLockNavHost(navController: NavHostController = rememberNavController()) {
    // MainActivity is singleTask: when the lock screen or a notification opens the edit screen while the
    // app is already running, the intent arrives here instead of creating a second activity.
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(navController, activity) {
        val listener = Consumer<Intent> { navController.handleDeepLink(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        // Short slide + fade; the Navigation default (a 700 ms cross-fade) feels sluggish here.
        enterTransition = { slideInHorizontally(tween(ANIM_MS)) { it / 6 } + fadeIn(tween(ANIM_MS)) },
        exitTransition = { fadeOut(tween(ANIM_MS / 2)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_MS)) { it / 6 } + fadeOut(tween(ANIM_MS)) },
    ) {
        composable(Routes.DASHBOARD) { entry ->
            val viewModel: DashboardViewModel = hiltViewModel()

            // The edit screen reports "remove this app" through *this entry's* SavedStateHandle. That is
            // not the ViewModel's own SavedStateHandle (each has a separate one), so it is read here
            // and forwarded. The key is cleared first so the request runs exactly once.
            val handle = entry.savedStateHandle
            val removeRequest by handle.getStateFlow<String?>(Routes.KEY_REMOVE_REQUEST, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(removeRequest) {
                removeRequest?.let { packageName ->
                    handle[Routes.KEY_REMOVE_REQUEST] = null
                    viewModel.remove(packageName)
                }
            }

            DashboardScreen(
                onAddApp = { if (navController.isResumed()) navController.navigate(Routes.ADD) },
                onEditApp = { if (navController.isResumed()) navController.navigate(Routes.edit(it)) },
                onOpenPermissions = { if (navController.isResumed()) navController.navigate(Routes.PERMISSIONS) },
                viewModel = viewModel,
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popIfResumed() })
        }

        composable(Routes.ADD) {
            AddAppScreen(
                onBack = { navController.popIfResumed() },
                onSaved = { navController.popIfResumed() },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument(Routes.ARG_PACKAGE) { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.EDIT_DEEP_LINK }),
        ) {
            EditAppScreen(
                onBack = { navController.popIfResumed() },
                onRemoveConfirmed = { packageName ->
                    if (navController.isResumed()) {
                        // The dashboard entry picks this up (see above), removes the app and shows Undo.
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Routes.KEY_REMOVE_REQUEST, packageName)
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}

// A fast double tap would otherwise navigate twice (two Add screens) or pop twice (leaving the app).
private fun NavHostController.isResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavHostController.popIfResumed() {
    if (isResumed()) popBackStack()
}
