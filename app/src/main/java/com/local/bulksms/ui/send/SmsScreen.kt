package com.local.bulksms.ui.send

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.components.RoundedActionIcon
import com.local.bulksms.ui.components.RoundedActionKind
import com.local.bulksms.ui.template.TemplateUiState

@Composable
fun SmsScreen(
    state: SendFlowUiState,
    templateState: TemplateUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    var templateMenuOpen by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var pendingTemplateId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("短信", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("模板", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundedActionIcon(
                        kind = RoundedActionKind.ADD,
                        onClick = { newTemplateName = ""; showCreateDialog = true },
                        modifier = Modifier.testTag("template-add"),
                    )
                    RoundedActionIcon(
                        kind = RoundedActionKind.REMOVE,
                        enabled = templateState.templates.size > 1,
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("template-delete"),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { templateMenuOpen = true },
                        modifier = Modifier.fillMaxWidth().testTag("template-selector"),
                    ) {
                        Text(templateState.editorName.ifBlank { state.selectedTemplateName.ifBlank { "选择模板" } })
                    }
                    DropdownMenu(
                        expanded = templateMenuOpen,
                        onDismissRequest = { templateMenuOpen = false },
                    ) {
                        templateState.templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    templateMenuOpen = false
                                    if (templateState.isDirty) {
                                        pendingTemplateId = template.id
                                    } else {
                                        callbacks.onTemplateSelected(template.id)
                                    }
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = callbacks.onSaveTemplate,
                    enabled = templateState.isDirty && templateState.editorBody.isNotBlank(),
                    modifier = Modifier.testTag("template-save"),
                ) { Text("保存") }
            }
            OutlinedTextField(
                value = templateState.editorBody.ifEmpty { state.selectedTemplateBody.orEmpty() },
                onValueChange = callbacks.onTemplateBodyChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .testTag("template-body"),
                placeholder = { Text("输入模板内容，如：您好，{A}……") },
                minLines = 2,
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.table?.columns.orEmpty().forEach { column ->
                    AssistChip(onClick = {}, label = { Text("{${column.name}}") })
                }
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("短信预览  ${state.drafts.size} 条", style = MaterialTheme.typography.titleMedium)
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

        Surface(shadowElevation = 5.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val simLabel = state.simOptions
                    .firstOrNull { it.subscriptionId == state.selectedSubscriptionId }
                    ?.displayLabel ?: "未选择 SIM"
                Column(Modifier.weight(1f)) {
                    Text(simLabel, style = MaterialTheme.typography.labelMedium)
                    Text("待发送 ${state.drafts.size} 条", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { showSendDialog = true },
                    enabled = state.drafts.isNotEmpty() && state.selectedPhoneColumn != null,
                ) { Text("确认并发送") }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("添加模板") },
            text = {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text("模板名称") },
                    singleLine = true,
                )
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = { callbacks.onCreateTemplate(newTemplateName); showCreateDialog = false },
                    enabled = newTemplateName.isNotBlank(),
                ) { Text("添加") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除当前模板？") },
            text = { Text("模板删除后无法恢复。") },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = { callbacks.onDeleteTemplate(); showDeleteDialog = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    pendingTemplateId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingTemplateId = null },
            title = { Text("放弃修改并切换？") },
            text = { Text("当前模板有尚未保存的修改。") },
            dismissButton = { TextButton(onClick = { pendingTemplateId = null }) { Text("取消") } },
            confirmButton = {
                Button(onClick = { callbacks.onTemplateSelected(id); pendingTemplateId = null }) {
                    Text("放弃并切换")
                }
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
                Button(onClick = { showSendDialog = false; callbacks.onConfirmSend() }) { Text("确认发送") }
            },
        )
    }
}
