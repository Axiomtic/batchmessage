package com.local.bulksms.ui.send

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.components.RoundedActionIcon
import com.local.bulksms.ui.components.RoundedActionKind
import com.local.bulksms.ui.icons.BulkSmsIcons
import com.local.bulksms.ui.template.TemplateUiState
import com.local.bulksms.ui.theme.neutralOutlinedTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(
    state: SendFlowUiState,
    templateState: TemplateUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    var templateMenuOpen by remember { mutableStateOf(false) }
    var simMenuOpen by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var pendingTemplateId by remember { mutableStateOf<String?>(null) }
    val controlsEnabled = state.sendProgress?.running != true
    val selectedCount = state.selectedDraftRowIds.count { selected ->
        state.drafts.any { it.rowId == selected }
    }
    val pendingMessageCount = state.drafts
        .filter { it.rowId in state.selectedDraftRowIds }
        .sumOf { draft ->
            PhoneNumberChecker.visibleNumbers(
                draft.phoneNumber, state.showAvailable, state.showUnavailable,
            ).size +
                PhoneNumberChecker.visibleNumbers(
                    draft.backupPhoneNumber, state.showAvailable, state.showUnavailable,
                ).size
        }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("template-card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(BulkSmsIcons.Template),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundedActionIcon(
                            kind = RoundedActionKind.ADD,
                            enabled = controlsEnabled,
                            onClick = { newTemplateName = ""; showCreateDialog = true },
                            modifier = Modifier.testTag("template-add"),
                        )
                        RoundedActionIcon(
                            kind = RoundedActionKind.REMOVE,
                            enabled = controlsEnabled && templateState.templates.size > 1,
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
                    ExposedDropdownMenuBox(
                        expanded = templateMenuOpen,
                        onExpandedChange = { if (controlsEnabled) templateMenuOpen = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = templateState.editorName,
                            onValueChange = callbacks.onTemplateNameChanged,
                            enabled = controlsEnabled,
                            label = { Text("模板名称") },
                            placeholder = { Text("选择或输入模板名称") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateMenuOpen)
                            },
                            colors = neutralOutlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("template-selector"),
                        )
                        ExposedDropdownMenu(
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
                        enabled = controlsEnabled &&
                            templateState.isDirty &&
                            templateState.editorName.isNotBlank() &&
                            templateState.editorBody.isNotBlank(),
                        modifier = Modifier.testTag("template-save"),
                    ) { Text("保存") }
                }

                val columns = state.table?.columns.orEmpty()
                OutlinedTextField(
                    value = templateState.editorBody.ifEmpty { state.selectedTemplateBody.orEmpty() },
                    onValueChange = callbacks.onTemplateBodyChanged,
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 82.dp)
                        .testTag("template-body"),
                    placeholder = { Text("输入模板内容，如：您好，{名字}……") },
                    colors = neutralOutlinedTextFieldColors(),
                    minLines = 2,
                    maxLines = 3,
                )
                if (columns.isNotEmpty()) {
                    Text(
                        "可用字段：${columns.joinToString("、") { it.name }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("template-fields-hint"),
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("preview-card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageReviewHeader(
                    drafts = state.drafts,
                    selectedRowIds = state.selectedDraftRowIds,
                    enabled = controlsEnabled,
                    onSelectAll = callbacks.onSelectAllDrafts,
                    draftsStale = state.draftsStale,
                    onRefresh = callbacks.onRefreshPreview,
                    showAvailable = state.showAvailable,
                    showUnavailable = state.showUnavailable,
                )
                MessageReviewList(
                    drafts = state.drafts,
                    selectedRowIds = state.selectedDraftRowIds,
                    enabled = controlsEnabled,
                    onSelectionChanged = callbacks.onDraftSelectionChanged,
                    showAvailable = state.showAvailable,
                    showUnavailable = state.showUnavailable,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.blockingError?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            val progress = state.sendProgress
            if (progress != null) {
                SendProgressFooter(
                    progress = progress,
                    onSendAgain = { showSendDialog = true },
                    canSendAgain = selectedCount > 0 &&
                        (state.selectedPhoneColumn != null || state.selectedBackupPhoneColumn != null) &&
                        state.simOptions.any { it.subscriptionId == state.selectedSubscriptionId },
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "待发送 $pendingMessageCount 条",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                if (state.selectedPhoneColumn != null || state.selectedBackupPhoneColumn != null) {
                                    "主/备用号码各发送一次，空号码自动忽略"
                                } else {
                                    "请在数据表点击列头设置电话号码列"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SendButtonWithSimMenu(
                            state = state,
                            enabled = controlsEnabled,
                            menuOpen = simMenuOpen,
                            onMenuOpenChange = { simMenuOpen = it },
                            onSendClick = { showSendDialog = true },
                            canSend = pendingMessageCount > 0 &&
                                (state.selectedPhoneColumn != null || state.selectedBackupPhoneColumn != null) &&
                                state.simOptions.any { it.subscriptionId == state.selectedSubscriptionId },
                            callbacks = callbacks,
                        )
                    }
                }
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
                    colors = neutralOutlinedTextFieldColors(),
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
            title = { Text("确认发送 $selectedCount 条短信？") },
            text = { Text("短信提交后无法撤回，可能产生运营商费用。") },
            dismissButton = { TextButton(onClick = { showSendDialog = false }) { Text("取消") } },
            confirmButton = {
                Button(onClick = { showSendDialog = false; callbacks.onRequestSend() }) {
                    Text("确认发送")
                }
            },
        )
    }
}

@Composable
private fun SendProgressFooter(
    progress: SendProgressUiState,
    onSendAgain: () -> Unit,
    canSendAgain: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progress.running) {
            Text(
                "正在发送 ${progress.processed}/${progress.total}",
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(
                progress = {
                    if (progress.total == 0) 0f else progress.processed.toFloat() / progress.total
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (progress.failed == 0) BulkSmsIcons.Success else BulkSmsIcons.Error,
                    ),
                    contentDescription = null,
                    tint = if (progress.failed == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text("发送完成", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "成功 ${progress.succeeded} 条，失败 ${progress.failed} 条",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onSendAgain, enabled = canSendAgain) { Text("再次发送") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendButtonWithSimMenu(
    state: SendFlowUiState,
    enabled: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    canSend: Boolean,
    callbacks: BulkSmsCallbacks,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSendClick,
            enabled = canSend && enabled,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                bottomStart = 22.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
            ),
            modifier = Modifier.testTag("send-selected"),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(BulkSmsIcons.Send),
                        contentDescription = null,
                    )
                    Text("确认并发送")
                }
                Text(
                    simCaption(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("send-sim-caption"),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(44.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 0.dp,
                        bottomStart = 0.dp,
                        topEnd = 22.dp,
                        bottomEnd = 22.dp,
                    ),
                )
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = enabled) {
                    when (state.simDetectionState) {
                        SimDetectionState.AVAILABLE -> onMenuOpenChange(true)
                        SimDetectionState.PERMISSION_REQUIRED -> callbacks.onRequestSimPermission()
                        SimDetectionState.EMPTY, SimDetectionState.ERROR ->
                            callbacks.onRefreshSimOptions()
                        SimDetectionState.LOADING -> Unit
                    }
                }
                .testTag("sim-menu-button"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)),
            )
            when (state.simDetectionState) {
                SimDetectionState.AVAILABLE -> Icon(
                    painter = painterResource(BulkSmsIcons.ArrowDropDown),
                    contentDescription = "选择 SIM",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                SimDetectionState.PERMISSION_REQUIRED -> Icon(
                    painter = painterResource(BulkSmsIcons.Sim),
                    contentDescription = "授权读取 SIM",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                SimDetectionState.EMPTY, SimDetectionState.ERROR -> Icon(
                    painter = painterResource(BulkSmsIcons.Refresh),
                    contentDescription = "重新检测 SIM",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                SimDetectionState.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }

            DropdownMenu(
                expanded = menuOpen && state.simDetectionState == SimDetectionState.AVAILABLE,
                onDismissRequest = { onMenuOpenChange(false) },
            ) {
                state.simOptions.forEach { sim ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (sim.subscriptionId == state.selectedSubscriptionId) {
                                    "✓ ${sim.displayLabel}"
                                } else {
                                    sim.displayLabel
                                },
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            callbacks.onSubscriptionSelected(sim.subscriptionId)
                        },
                        modifier = Modifier.testTag("sim-option-${sim.subscriptionId}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun simCaption(state: SendFlowUiState): String {
    val selected = state.simOptions.firstOrNull {
        it.subscriptionId == state.selectedSubscriptionId
    }
    return selected?.displayLabel ?: when (state.simDetectionState) {
        SimDetectionState.PERMISSION_REQUIRED -> "需授权读取 SIM"
        SimDetectionState.LOADING -> "正在检测 SIM"
        SimDetectionState.EMPTY -> "无 SIM 可用"
        SimDetectionState.ERROR -> "SIM 检测失败"
        SimDetectionState.AVAILABLE -> "选择 SIM"
    }
}
