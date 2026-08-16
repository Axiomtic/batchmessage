package com.local.bulksms.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val onXlsxImport: (InputStream) -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onCellChanged: (Long, Int, String) -> Unit = { _, _, _ -> },
    val onAddRow: () -> Unit = {},
    val onAddColumn: () -> Unit = {},
    val onDeleteRow: (Long) -> Unit = {},
    val onDeleteColumn: (Int) -> Unit = {},
    val onHeaderModeChanged: (Boolean) -> Unit = {},
    val onPhoneColumnSelected: (Int) -> Unit = {},
    val onSubscriptionSelected: (Int) -> Unit = {},
    val onTemplateSelected: (String) -> Unit = {},
    val onTemplateBodyChanged: (String) -> Unit = {},
    val onCreateTemplate: (String) -> Unit = {},
    val onSaveTemplate: () -> Unit = {},
    val onDeleteTemplate: () -> Unit = {},
    val onDraftChanged: (Long, String) -> Unit = { _, _ -> },
    val onDraftSyncChanged: (Long, Boolean) -> Unit = { _, _ -> },
    val onSyncAll: () -> Unit = {},
    val onUnsyncAll: () -> Unit = {},
    val onConfirmSend: () -> Unit = {},
)

@Composable
fun BulkSmsApp(
    sendState: SendFlowUiState,
    templateState: TemplateUiState,
    callbacks: BulkSmsCallbacks,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.DATA.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.DATA.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.label.take(1)) },
                        label = { Text(destination.label) },
                    )
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
