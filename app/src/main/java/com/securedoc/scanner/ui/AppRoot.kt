@file:OptIn(ExperimentalLayoutApi::class)

package com.securedoc.scanner.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securedoc.scanner.AppContainer
import com.securedoc.scanner.ui.components.AuthGate
import com.securedoc.scanner.ui.screens.EditScreen
import com.securedoc.scanner.ui.screens.HomeScreen
import com.securedoc.scanner.ui.screens.LibraryScreen
import com.securedoc.scanner.ui.screens.ScanScreen
import com.securedoc.scanner.ui.screens.SettingsScreen
import com.securedoc.scanner.ui.screens.VaultScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer manquant") }

fun Context.findActivity(): AppCompatActivity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is AppCompatActivity) return ctx
        ctx = ctx.baseContext
    }
    error("Activité introuvable")
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Accueil", Icons.Filled.Home),
    Tab(Routes.LIBRARY, "Bibliothèque", Icons.Filled.FolderOpen),
    Tab(Routes.VAULT, "Coffre-fort", Icons.Filled.Lock),
    Tab(Routes.SETTINGS, "Paramètres", Icons.Filled.Settings),
)

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val SCAN = "scan?import={import}"
    const val EDIT = "edit/{id}"
    fun scan(import: Boolean) = "scan?import=$import"
    fun edit(id: Long) = "edit/$id"
}

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val locked by container.lockController.locked.collectAsStateWithLifecycle()

    // Écran verrouillé : rien du contenu n'est composé derrière.
    if (locked) {
        AuthGate(
            title = "SecureDoc est verrouillé",
            subtitle = "Saisissez votre code PIN",
            onUnlocked = container.lockController::unlock,
        )
        return
    }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            AppNavHost(navController)
        }
        if (showBar) {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { navController.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScan = { nav.navigate(Routes.scan(false)) },
                onImport = { nav.navigate(Routes.scan(true)) },
                onOpen = { nav.navigate(Routes.edit(it)) },
                onSeeAll = { nav.navigateTab(Routes.LIBRARY) },
            )
        }
        composable(
            Routes.SCAN,
            arguments = listOf(navArgument("import") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            ScanScreen(
                importMode = entry.arguments?.getBoolean("import") ?: false,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.edit(id)) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            EditScreen(
                documentId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(onOpen = { nav.navigate(Routes.edit(it)) })
        }
        composable(Routes.VAULT) {
            VaultScreen(
                onOpen = { nav.navigate(Routes.edit(it)) },
                onScan = { nav.navigate(Routes.scan(false)) },
                onOpenSettings = { nav.navigateTab(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
