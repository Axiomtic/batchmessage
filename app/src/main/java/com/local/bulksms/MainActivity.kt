package com.local.bulksms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.sms.SimSubscriptionProvider
import com.local.bulksms.sms.SendPreferences
import com.local.bulksms.sms.SmsPermissions
import com.local.bulksms.sms.SmsSendingService
import com.local.bulksms.ui.BulkSmsApp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
import com.local.bulksms.ui.template.TemplateViewModel
import com.local.bulksms.ui.theme.BulkSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val incomingExcelUris = Channel<Uri>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enqueueIncomingExcel(intent)
        setContent {
            BulkSmsTheme {
                val app = application as BulkSmsApplication
                val factory = remember(app.repository) { BulkSmsViewModelFactory(app.repository) }
                val sendFlowViewModel: SendFlowViewModel = viewModel(factory = factory)
                val templateViewModel: TemplateViewModel = viewModel(factory = factory)
                val sendState by sendFlowViewModel.state.collectAsState()
                val templateState by templateViewModel.state.collectAsState()
                var externalDataNavigationRequest by remember { mutableLongStateOf(0L) }
                var simPermissionRequested by remember { mutableStateOf(false) }
                val composeScope = rememberCoroutineScope()
                val simProvider = remember { SimSubscriptionProvider(this@MainActivity) }
                val sendPreferences = remember { SendPreferences(this@MainActivity) }
                val reloadSimOptions: () -> Unit = {
                    sendFlowViewModel.setSimLoading()
                    runCatching { simProvider.active() }.fold(
                        onSuccess = sendFlowViewModel::setSimOptions,
                        onFailure = { error ->
                            sendFlowViewModel.setSimDetectionError(error.message ?: "SIM 检测失败")
                        },
                    )
                }
                val simPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) reloadSimOptions() else sendFlowViewModel.setSimPermissionRequired()
                }
                val startSelectedSend: () -> Unit = {
                    composeScope.launch {
                        sendFlowViewModel.createSelectedSendTask()?.let { taskId ->
                            sendFlowViewModel.observeSendTask(taskId)
                            SmsSendingService.start(
                                context = this@MainActivity,
                                taskId = taskId,
                                sendIntervalMillis = sendState.sendIntervalMillis,
                            )
                        }
                    }
                    Unit
                }
                val sendPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { grants ->
                    val smsGranted = grants[Manifest.permission.SEND_SMS]
                        ?: (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.SEND_SMS,
                        ) == PackageManager.PERMISSION_GRANTED)
                    if (smsGranted) startSelectedSend() else sendFlowViewModel.setSendPermissionDenied()
                }

                LaunchedEffect(Unit) {
                    sendFlowViewModel.setSendInterval(sendPreferences.sendIntervalMillis)
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_PHONE_STATE,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        reloadSimOptions()
                    } else {
                        // Ask for the SIM permission as soon as the app opens instead
                        // of waiting for the user to visit the settings page.
                        sendFlowViewModel.setSimPermissionRequired()
                        if (!simPermissionRequested) {
                            simPermissionRequested = true
                            simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    }
                }
                LaunchedEffect(sendState.workspaceReady) {
                    if (!sendState.workspaceReady) return@LaunchedEffect
                    for (uri in incomingExcelUris) {
                        externalDataNavigationRequest++
                        runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use { input ->
                                    sendFlowViewModel.requestXlsxImport(input)
                                } ?: error("无法打开分享的文件")
                            }
                        }.onFailure {
                            sendFlowViewModel.reportImportError("无法读取分享的 Excel 文件")
                        }
                    }
                }
                LaunchedEffect(
                    templateState.templates,
                    templateState.selectedTemplateId,
                    sendState.selectedTemplateId,
                ) {
                    if (templateState.selectedTemplateId == null && templateState.templates.isNotEmpty()) {
                        val target = templateState.templates.firstOrNull {
                            it.id == sendState.selectedTemplateId
                        } ?: templateState.templates.first()
                        templateViewModel.selectTemplate(target.id)
                        sendFlowViewModel.selectTemplate(target.id, target.body, target.name)
                    }
                }

                BulkSmsApp(
                    sendState = sendState,
                    templateState = templateState,
                    callbacks = BulkSmsCallbacks(
                        onClipboardImport = sendFlowViewModel::requestClipboardImport,
                        onExcelImport = sendFlowViewModel::requestExcelImport,
                        onConfirmImport = sendFlowViewModel::confirmPendingImport,
                        onCancelImport = sendFlowViewModel::cancelPendingImport,
                        onHeaderModeChanged = sendFlowViewModel::setFirstRowIsHeader,
                        onCellChanged = sendFlowViewModel::editCell,
                        onPhoneColumnSelected = sendFlowViewModel::selectPhoneColumn,
                        onBackupPhoneColumnSelected = sendFlowViewModel::selectBackupPhoneColumn,
                        onAddRow = sendFlowViewModel::addRow,
                        onAddColumn = sendFlowViewModel::addColumn,
                        onDeleteRow = sendFlowViewModel::deleteRow,
                        onDeleteColumn = sendFlowViewModel::deleteColumn,
                        onDraftSelectionChanged = sendFlowViewModel::toggleDraftSelection,
                        onSelectAllDrafts = sendFlowViewModel::selectAllDrafts,
                        onSubscriptionSelected = sendFlowViewModel::selectSubscription,
                        onRequestSimPermission = {
                            simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        },
                        onRefreshSimOptions = reloadSimOptions,
                        onSendIntervalChanged = { intervalMillis ->
                            sendPreferences.sendIntervalMillis = intervalMillis
                            sendFlowViewModel.setSendInterval(intervalMillis)
                        },
                        onRequestSend = {
                            val missing = SmsPermissions.requiredRuntimePermissions(Build.VERSION.SDK_INT)
                                .filter { permission ->
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        permission,
                                    ) != PackageManager.PERMISSION_GRANTED
                                }
                            if (missing.isEmpty()) {
                                startSelectedSend()
                            } else {
                                sendPermissionLauncher.launch(missing.toTypedArray())
                            }
                        },
                        onTemplateSelected = { id ->
                            templateViewModel.selectTemplate(id)
                            sendFlowViewModel.selectTemplate(id)
                        },
                        onTemplateNameChanged = templateViewModel::setEditorName,
                        onTemplateBodyChanged = { body ->
                            templateViewModel.setEditorBody(body)
                            sendFlowViewModel.updateTemplateBody(body)
                        },
                        onCreateTemplate = { name ->
                            templateViewModel.create(name)?.let { created ->
                                sendFlowViewModel.selectTemplate(created.id, created.body, created.name)
                            }
                        },
                        onSaveTemplate = {
                            templateViewModel.saveSelected()?.let { saved ->
                                sendFlowViewModel.selectTemplate(saved.id, saved.body, saved.name)
                            }
                        },
                        onDeleteTemplate = {
                            templateViewModel.deleteSelected()?.let { nextId ->
                                templateState.templates.firstOrNull { it.id == nextId }?.let { next ->
                                    sendFlowViewModel.selectTemplate(next.id, next.body, next.name)
                                }
                            }
                        },
                    ),
                    externalDataNavigationRequest = externalDataNavigationRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueIncomingExcel(intent)
    }

    private fun enqueueIncomingExcel(intent: Intent?) {
        intent?.incomingExcelUri()?.let(incomingExcelUris::trySend)
    }
}

private fun Intent.incomingExcelUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    else -> null
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
