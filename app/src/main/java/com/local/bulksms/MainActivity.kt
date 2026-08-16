package com.local.bulksms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.sms.SimSubscriptionProvider
import com.local.bulksms.ui.send.SendWorkbenchCallbacks
import com.local.bulksms.ui.send.SendWorkbenchScreen
import com.local.bulksms.ui.send.SendFlowViewModel
import com.local.bulksms.ui.template.TemplateScreen
import com.local.bulksms.ui.template.TemplateViewModel
import com.local.bulksms.ui.theme.BulkSmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BulkSmsTheme {
                val app = application as BulkSmsApplication
                val factory = remember(app.repository) { BulkSmsViewModelFactory(app.repository) }
                val sendFlowViewModel: SendFlowViewModel = viewModel(factory = factory)
                val templateViewModel: TemplateViewModel = viewModel(factory = factory)
                val sendState by sendFlowViewModel.state.collectAsState()
                val templateState by templateViewModel.state.collectAsState()
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    sendFlowViewModel.setSimOptions(SimSubscriptionProvider(this@MainActivity).active())
                }

                NavHost(navController = navController, startDestination = "workbench") {
                    composable("workbench") {
                        SendWorkbenchScreen(
                            state = sendState,
                            callbacks = SendWorkbenchCallbacks(
                                onOpenTemplateManager = {
                                    sendState.selectedTemplateId?.let(templateViewModel::selectTemplate)
                                    navController.navigate("templates")
                                },
                                onClipboardImport = sendFlowViewModel::requestClipboardImport,
                                onXlsxImport = sendFlowViewModel::requestXlsxImport,
                                onConfirmImport = sendFlowViewModel::confirmPendingImport,
                                onCancelImport = sendFlowViewModel::cancelPendingImport,
                                onHeaderModeChanged = sendFlowViewModel::setFirstRowIsHeader,
                                onCellChanged = { edit ->
                                    sendFlowViewModel.editCell(edit.rowId, edit.columnIndex, edit.value)
                                },
                                onHeaderChanged = { edit ->
                                    sendFlowViewModel.editHeader(edit.columnIndex, edit.value)
                                },
                                onPhoneColumnSelected = sendFlowViewModel::selectPhoneColumn,
                                onAddRow = sendFlowViewModel::addRow,
                                onAddColumn = sendFlowViewModel::addColumn,
                                onDeleteLastRow = sendFlowViewModel::deleteLastRow,
                                onDeleteLastColumn = sendFlowViewModel::deleteLastColumn,
                                onClearTable = sendFlowViewModel::clearTable,
                                onTemplateSelected = sendFlowViewModel::selectTemplate,
                                onTemplateBodyChanged = sendFlowViewModel::updateTemplateBody,
                                onOverwriteTemplate = sendFlowViewModel::overwriteSelectedTemplate,
                                onSaveTemplateAs = sendFlowViewModel::saveSelectedTemplateAs,
                                onDraftChanged = sendFlowViewModel::editDraft,
                                onDraftSyncChanged = sendFlowViewModel::setDraftSynced,
                                onSyncAll = sendFlowViewModel::syncAllDrafts,
                                onUnsyncAll = sendFlowViewModel::unsyncAllDrafts,
                                onSubscriptionSelected = sendFlowViewModel::selectSubscription,
                            ),
                        )
                    }
                    composable("templates") {
                        TemplateScreen(
                            state = templateState,
                            availableVariables = sendState.table?.columns?.map { it.name }.orEmpty(),
                            onSelect = { id ->
                                templateViewModel.selectTemplate(id)
                                sendFlowViewModel.selectTemplate(id)
                            },
                            onStartNew = templateViewModel::startNew,
                            onNameChanged = templateViewModel::setEditorName,
                            onBodyChanged = templateViewModel::setEditorBody,
                            onOverwrite = {
                                val current = templateState
                                templateViewModel.overwrite()
                                if (current.selectedTemplateId == sendState.selectedTemplateId) {
                                    current.selectedTemplateId?.let { id ->
                                        sendFlowViewModel.selectTemplate(
                                            id,
                                            current.editorBody,
                                            current.editorName,
                                        )
                                    }
                                }
                            },
                            onSaveAs = { name ->
                                templateViewModel.saveAs(name)?.let { id ->
                                    sendFlowViewModel.selectTemplate(
                                        id,
                                        templateState.editorBody,
                                        name.trim(),
                                    )
                                }
                            },
                            onDelete = templateViewModel::delete,
                            onBack = navController::popBackStack,
                        )
                    }
                }
            }
        }
    }
}

private class BulkSmsViewModelFactory(
    private val repository: BulkSmsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SendFlowViewModel::class.java) ->
            SendFlowViewModel(repository = repository) as T
        modelClass.isAssignableFrom(TemplateViewModel::class.java) ->
            TemplateViewModel.fromDao(repository.templateDao) as T
        else -> error("未知 ViewModel：${modelClass.name}")
    }
}
