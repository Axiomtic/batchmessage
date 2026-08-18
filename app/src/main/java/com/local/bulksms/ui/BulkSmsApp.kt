package com.local.bulksms.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.local.bulksms.ui.data.DataScreen
import com.local.bulksms.ui.send.SendFlowUiState
import com.local.bulksms.ui.send.SmsScreen
import com.local.bulksms.ui.settings.SettingsScreen
import com.local.bulksms.ui.template.TemplateUiState
import java.io.InputStream

data class BulkSmsCallbacks(
    val onClipboardImport: (String) -> Unit = {},
    val onExcelImport: (InputStream) -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onCellChanged: (Long, Int, String) -> Unit = { _, _, _ -> },
    val onAddRow: () -> Unit = {},
    val onAddColumn: () -> Unit = {},
    val onDeleteRow: (Long) -> Unit = {},
    val onDeleteColumn: (Int) -> Unit = {},
    val onHeaderModeChanged: (Boolean) -> Unit = {},
    val onPhoneColumnSelected: (Int) -> Unit = {},
    val onBackupPhoneColumnSelected: (Int) -> Unit = {},
    val onColumnHeaderClicked: (Int) -> Unit = {},
    val onSubscriptionSelected: (Int) -> Unit = {},
    val onRequestSimPermission: () -> Unit = {},
    val onRefreshSimOptions: () -> Unit = {},
    val onSendIntervalChanged: (Long) -> Unit = {},
    val onTemplateSelected: (String) -> Unit = {},
    val onTemplateNameChanged: (String) -> Unit = {},
    val onTemplateBodyChanged: (String) -> Unit = {},
    val onCreateTemplate: (String) -> Unit = {},
    val onSaveTemplate: () -> Unit = {},
    val onDeleteTemplate: () -> Unit = {},
    val onDraftSelectionChanged: (Long, Boolean) -> Unit = { _, _ -> },
    val onSelectAllDrafts: (Boolean) -> Unit = {},
    val onRefreshPreview: () -> Unit = {},
    val onRequestSend: () -> Unit = {},
)

@Composable
fun BulkSmsApp(
    sendState: SendFlowUiState,
    templateState: TemplateUiState,
    callbacks: BulkSmsCallbacks,
    externalDataNavigationRequest: Long = 0L,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.DATA.route

    LaunchedEffect(externalDataNavigationRequest) {
        if (externalDataNavigationRequest > 0L && currentRoute != AppDestination.DATA.route) {
            navController.navigate(AppDestination.DATA.route) {
                popUpTo(AppDestination.DATA.route)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp,
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav-${destination.route}"),
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(AppDestination.DATA.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = "${destination.label}图标",
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.DATA.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.DATA.route) {
                DataScreen(sendState, callbacks)
            }
            composable(AppDestination.SMS.route) {
                SmsScreen(sendState, templateState, callbacks)
            }
            composable(AppDestination.SETTINGS.route) {
                SettingsScreen(sendState, callbacks)
            }
        }
    }
}
