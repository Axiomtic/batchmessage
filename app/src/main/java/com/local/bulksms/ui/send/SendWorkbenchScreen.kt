package com.local.bulksms.ui.send

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.InputStream

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

data class SendWorkbenchCallbacks(
    val onOpenTemplateManager: () -> Unit = {},
    val onClipboardImport: (String) -> Unit = {},
    val onXlsxImport: (InputStream) -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onHeaderModeChanged: (Boolean) -> Unit = {},
    val onCellChanged: (CellEdit) -> Unit = {},
    val onHeaderChanged: (HeaderEdit) -> Unit = {},
    val onPhoneColumnSelected: (Int) -> Unit = {},
    val onAddRow: () -> Unit = {},
    val onAddColumn: () -> Unit = {},
    val onDeleteLastRow: () -> Unit = {},
    val onDeleteLastColumn: () -> Unit = {},
    val onClearTable: () -> Unit = {},
    val onTemplateBodyChanged: (String) -> Unit = {},
    val onTemplateSelected: (String) -> Unit = {},
    val onOverwriteTemplate: () -> Unit = {},
    val onSaveTemplateAs: (String) -> Unit = {},
    val onDraftChanged: (Long, String) -> Unit = { _, _ -> },
    val onDraftSyncChanged: (Long, Boolean) -> Unit = { _, _ -> },
    val onSyncAll: () -> Unit = {},
    val onUnsyncAll: () -> Unit = {},
    val onSubscriptionSelected: (Int) -> Unit = {},
    val onConfirmSend: () -> Unit = {},
)

@Composable
fun SendWorkbenchScreen(
    state: SendFlowUiState,
    callbacks: SendWorkbenchCallbacks,
    modifier: Modifier = Modifier,
) {
    var tableMenuExpanded by remember { mutableStateOf(false) }
    var templateMenuExpanded by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var showSendDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use(callbacks.onXlsxImport)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("send-workbench"),
    ) {
        Surface(color = MaterialTheme.colorScheme.primary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "批量短信助手",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = callbacks.onOpenTemplateManager) {
                    Text("模板管理", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("数据表格", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("首行为字段名", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = state.table?.firstRowIsHeader == true,
                        onCheckedChange = callbacks.onHeaderModeChanged,
                        modifier = Modifier.testTag("workbench-header-switch"),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { fileLauncher.launch(arrayOf(XLSX_MIME)) }) { Text("Excel") }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val value = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        callbacks.onClipboardImport(value)
                    },
                ) { Text("剪切板") }
                TextButton(onClick = callbacks.onAddRow) { Text("+ 行") }
                TextButton(onClick = callbacks.onAddColumn) { Text("+ 列") }
                Box {
                    TextButton(onClick = { tableMenuExpanded = true }) { Text("更多") }
                    DropdownMenu(
                        expanded = tableMenuExpanded,
                        onDismissRequest = { tableMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除最后一行") },
                            onClick = { tableMenuExpanded = false; callbacks.onDeleteLastRow() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除最后一列") },
                            onClick = { tableMenuExpanded = false; callbacks.onDeleteLastColumn() },
                        )
                        DropdownMenuItem(
                            text = { Text("清空表格", color = MaterialTheme.colorScheme.error) },
                            onClick = { tableMenuExpanded = false; callbacks.onClearTable() },
                        )
                    }
                }
            }
            state.table?.let { table ->
                EditableTable(
                    table = table,
                    onCellChanged = callbacks.onCellChanged,
                    onHeaderChanged = callbacks.onHeaderChanged,
                    onPhoneColumnSelected = callbacks.onPhoneColumnSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(0.72f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(
                        onClick = { templateMenuExpanded = true },
                        modifier = Modifier.testTag("template-selector"),
                    ) {
                        Column {
                            Text("现场模板", style = MaterialTheme.typography.labelSmall)
                            Text(
                                state.selectedTemplateName.ifBlank { "未选择模板" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = templateMenuExpanded,
                        onDismissRequest = { templateMenuExpanded = false },
                    ) {
                        state.templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    templateMenuExpanded = false
                                    callbacks.onTemplateSelected(template.id)
                                },
                            )
                        }
                    }
                }
                Row {
                    TextButton(onClick = callbacks.onOverwriteTemplate) { Text("覆盖保存") }
                    TextButton(
                        onClick = {
                            saveAsName = "${state.selectedTemplateName.ifBlank { "新模板" }} 副本"
                            showSaveAsDialog = true
                        },
                    ) { Text("另存为") }
                }
            }
            OutlinedTextField(
                value = state.selectedTemplateBody.orEmpty(),
                onValueChange = callbacks.onTemplateBodyChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("inline-template-body"),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待发送短信  ${state.drafts.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    TextButton(onClick = callbacks.onSyncAll) { Text("全部同步") }
                    TextButton(onClick = callbacks.onUnsyncAll) {
                        Text("全部取消", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            MessageReviewList(
                drafts = state.drafts,
                onEdit = callbacks.onDraftChanged,
                onSyncChanged = callbacks.onDraftSyncChanged,
                modifier = Modifier.weight(1f),
            )
        }

        Surface(shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.simOptions.isEmpty()) {
                    Text("未检测到 SIM", style = MaterialTheme.typography.labelMedium)
                } else {
                    state.simOptions.forEach { sim ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.selectedSubscriptionId == sim.subscriptionId,
                                onClick = { callbacks.onSubscriptionSelected(sim.subscriptionId) },
                            )
                            Text(sim.displayLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(
                    "待发送 ${state.drafts.size} 条",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Button(onClick = { showSendDialog = true }) { Text("确认并发送") }
            }
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = callbacks.onCancelImport,
            title = { Text("覆盖现有数据？") },
            text = {
                Text("导入会替换当前表格和同步短信；已取消“与表同步”的短信会保留不动。")
            },
            dismissButton = { TextButton(onClick = callbacks.onCancelImport) { Text("取消") } },
            confirmButton = { Button(onClick = callbacks.onConfirmImport) { Text("覆盖并导入") } },
        )
    }

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("另存为新模板") },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text("模板名称") },
                    singleLine = true,
                )
            },
            dismissButton = { TextButton(onClick = { showSaveAsDialog = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        callbacks.onSaveTemplateAs(saveAsName)
                        showSaveAsDialog = false
                    },
                    enabled = saveAsName.isNotBlank(),
                ) { Text("保存") }
            },
        )
    }

    if (showSendDialog) {
        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = { Text("确认发送 ${state.drafts.size} 条短信？") },
            text = { Text("短信提交后无法撤回，可能产生运营商费用。") },
            dismissButton = { TextButton(onClick = { showSendDialog = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        showSendDialog = false
                        callbacks.onConfirmSend()
                    },
                ) { Text("确认发送") }
            },
        )
    }
}
