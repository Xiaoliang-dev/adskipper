package com.adskipper.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adskipper.RuleEditorActivity
import com.adskipper.data.RuleEntity
import com.adskipper.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "主页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Rules : Screen("rules", "规则", Icons.Filled.RuleFolder, Icons.Outlined.RuleFolder)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Rules, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdSkipperApp(
    viewModel: MainViewModel,
    onOpenAccessibilitySettings: () -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val navController = androidx.navigation.compose.rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (currentRoute == screen.route) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onNavigateToRules = {
                        navController.navigate(Screen.Rules.route)
                    },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules
                )
            }
            composable(Screen.Rules.route) {
                RulesScreen(
                    viewModel = viewModel,
                    onAddRule = {
                        context.startActivity(RuleEditorActivity.createIntent(context))
                    },
                    onEditRule = { rule ->
                        context.startActivity(
                            RuleEditorActivity.createIntent(context, rule.id)
                        )
                    },
                    onExportRules = onExportRules,
                    onImportRules = onImportRules,
                    onCopyToClipboard = {
                        viewModel.copyRulesToClipboard()
                    },
                    onImportFromClipboard = {
                        viewModel.importFromClipboard()
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onResetStats = { viewModel.resetStats() },
                    onDeleteAllRules = { viewModel.deleteAllRules() }
                )
            }
        }
    }
}