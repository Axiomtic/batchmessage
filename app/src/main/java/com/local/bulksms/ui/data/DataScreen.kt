package com.local.bulksms.ui.data

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.PhoneAvailability
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.components.RoundedIconAction
import com.local.bulksms.ui.icons.BulkSmsIcons
import com.local.bulksms.ui.send.EditableTable
import com.local.bulksms.ui.send.SendFlowUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val EXCEL_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
)

private sealed interface DeleteTarget {
    data class Row(val rowId: Long, val ordinal: Int) : DeleteTarget
    data class Column(val index: Int, val address: String) : DeleteTarget
}

@Composable
fun DataScreen(
    state: SendFlowUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    val scope = rememberCoroutineScope()
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Parsing a large workbook blocks the UI thread; do it off the main thread.
        uri?.let { resolved ->
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openInputStream(resolved)?.use(callbacks.onExcelImport)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("导入数据", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ImportCard(
                label = "文件",
                hint = "Excel .xlsx / .xls",
                iconRes = BulkSmsIcons.File,
                modifier = Modifier.weight(1f).testTag("import-file"),
                onClick = { fileLauncher.launch(EXCEL_MIME_TYPES) },
            )
            ImportCard(
                label = "剪贴板",
                hint = "粘贴表格数据",
                iconRes = BulkSmsIcons.Clipboard,
                modifier = Modifier.weight(1f).testTag("import-clipboard"),
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    callbacks.onClipboardImport(text)
                },
            )
        }

        state.table?.let { table ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("数据表格", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${table.rows.size} 行 · ${table.columns.size} 列",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RoundedIconAction(
                        iconRes = BulkSmsIcons.Share,
                        contentDescription = "导出表格",
                        onClick = callbacks.onExportTable,
                        modifier = Modifier.testTag("export-table"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("首行作为字段", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "开启后第一行文本作为字段名，可直接在模板中用 {字段名}；点击列头可设主/备用电话号码列",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = table.firstRowIsHeader,
                    onCheckedChange = callbacks.onHeaderModeChanged,
                    modifier = Modifier.testTag("first-row-as-fields"),
                )
            }

            val stats = phoneStats(table)
            if (stats.total > 0) {
                Text(
                    "电话号码  有效 ${stats.available} · 无效 ${stats.invalid} · 空 ${stats.empty}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("phone-stats"),
                )
            }

            // A single filter button (WPS-style) at the top-right of the table
            // viewport opens a dropdown with checkbox options for which phone
            // categories stay visible; the share button lives in the block header
            // above. Without a phone column there is nothing to filter.
            val hasPhoneColumn = table.phoneColumnIndex != null || table.backupPhoneColumnIndex != null
            var filterMenuOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                EditableTable(
                    table = table,
                    onCellChanged = { callbacks.onCellChanged(it.rowId, it.columnIndex, it.value) },
                    onAddRow = callbacks.onAddRow,
                    onAddColumn = callbacks.onAddColumn,
                    onColumnHeaderClicked = callbacks.onColumnHeaderClicked,
                    showAvailable = state.showAvailable,
                    showUnavailable = state.showUnavailable,
                    onDeleteRowRequested = { rowId ->
                        val ordinal = table.rows.indexOfFirst { it.id == rowId } + 1
                        deleteTarget = DeleteTarget.Row(rowId, ordinal)
                    },
                    onDeleteColumnRequested = { index ->
                        deleteTarget = DeleteTarget.Column(index, table.columns[index].name)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 4.dp),
                ) {
                    RoundedIconAction(
                        iconRes = BulkSmsIcons.Filter,
                        contentDescription = "筛选电话",
                        enabled = hasPhoneColumn,
                        onClick = { filterMenuOpen = true },
                        modifier = Modifier.testTag("filter-phone"),
                    )
                    DropdownMenu(
                        expanded = filterMenuOpen,
                        onDismissRequest = { filterMenuOpen = false },
                    ) {
                        PhoneFilterRow(
                            label = "全选",
                            checked = state.showAvailable && state.showUnavailable,
                            testTag = "filter-select-all",
                            onToggle = {
                                val all = state.showAvailable && state.showUnavailable
                                callbacks.onShowAvailableChanged(!all)
                                callbacks.onShowUnavailableChanged(!all)
                            },
                        )
                        HorizontalDivider()
                        PhoneFilterRow(
                            label = "可用电话",
                            checked = state.showAvailable,
                            testTag = "toggle-available",
                            onToggle = { callbacks.onShowAvailableChanged(!state.showAvailable) },
                        )
                        PhoneFilterRow(
                            label = "不可用电话",
                            checked = state.showUnavailable,
                            testTag = "toggle-unavailable",
                            onToggle = { callbacks.onShowUnavailableChanged(!state.showUnavailable) },
                        )
                    }
                }
            }
        }

        state.blockingError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = callbacks.onCancelImport,
            title = { Text("覆盖现有数据？") },
            text = { Text("导入会替换当前表格，并重新生成短信预览。") },
            dismissButton = { TextButton(onClick = callbacks.onCancelImport) { Text("取消") } },
            confirmButton = { Button(onClick = callbacks.onConfirmImport) { Text("覆盖并导入") } },
        )
    }

    deleteTarget?.let { target ->
        val title = when (target) {
            is DeleteTarget.Row -> "删除第 ${target.ordinal} 行？"
            is DeleteTarget.Column -> "删除第 ${target.address} 列？"
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(title) },
            text = { Text("删除后无法撤销。") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            is DeleteTarget.Row -> callbacks.onDeleteRow(target.rowId)
                            is DeleteTarget.Column -> callbacks.onDeleteColumn(target.index)
                        }
                        deleteTarget = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

private data class PhoneStats(val available: Int, val invalid: Int, val empty: Int) {
    val total: Int get() = available + invalid + empty
}

private fun phoneStats(table: ImportedTable): PhoneStats {
    val indexes = listOfNotNull(table.phoneColumnIndex, table.backupPhoneColumnIndex)
    if (indexes.isEmpty()) return PhoneStats(0, 0, 0)
    var available = 0
    var invalid = 0
    var empty = 0
    for (row in table.rows) {
        for (index in indexes) {
            val cell = row.cells.getOrNull(index).orEmpty()
            val numbers = PhoneNumberChecker.extractPhoneNumbers(cell)
            when {
                numbers.isNotEmpty() -> {
                    available += numbers.count {
                        PhoneNumberChecker.availability(it) == PhoneAvailability.AVAILABLE
                    }
                    invalid += numbers.count {
                        PhoneNumberChecker.availability(it) != PhoneAvailability.AVAILABLE
                    }
                }
                cell.isBlank() -> empty++
                else -> invalid++
            }
        }
    }
    return PhoneStats(available, invalid, empty)
}

@Composable
private fun ImportCard(
    label: String,
    hint: String,
    iconRes: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** One checkbox row inside the WPS-style phone filter dropdown. */
@Composable
private fun PhoneFilterRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
