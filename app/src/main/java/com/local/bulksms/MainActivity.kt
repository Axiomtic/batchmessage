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
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.sms.SimSubscriptionProvider
import com.local.bulksms.ui.BulkSmsApp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
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

                LaunchedEffect(Unit) {
                    sendFlowViewModel.setSimOptions(SimSubscriptionProvider(this@MainActivity).active())
                }

                BulkSmsApp(
                    sendState = sendState,
                    templateState = templateState,
                    callbacks = BulkSmsCallbacks(
                        onClipboardImport = sendFlowViewModel::requestClipboardImport,
                        onXlsxImport = sendFlowViewModel::requestXlsxImport,
                        onConfirmImport = sendFlowViewModel::confirmPendingImport,
                        onCancelImport = sendFlowViewModel::cancelPendingImport,
                        onHeaderModeChanged = sendFlowViewModel::setFirstRowIsHeader,
                        onCellChanged = sendFlowViewModel::editCell,
                        onPhoneColumnSelected = sendFlowViewModel::selectPhoneColumn,
                        onAddRow = sendFlowViewModel::addRow,
                        onAddColumn = sendFlowViewModel::addColumn,
                        onDeleteRow = sendFlowViewModel::deleteRow,
                        onDeleteColumn = sendFlowViewModel::deleteColumn,
                        onDraftChanged = sendFlowViewModel::editDraft,
                        onDraftSyncChanged = sendFlowViewModel::setDraftSynced,
                        onSyncAll = sendFlowViewModel::syncAllDrafts,
                        onUnsyncAll = sendFlowViewModel::unsyncAllDrafts,
                        onSubscriptionSelected = sendFlowViewModel::selectSubscription,
                        onTemplateSelected = { id ->
                            templateViewModel.selectTemplate(id)
                            sendFlowViewModel.selectTemplate(id)
                        },
                        onTemplateBodyChanged = { body ->
                            templateViewModel.setEditorBody(body)
                            sendFlowViewModel.updateTemplateBody(body)
                        },
                    ),
                )
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
