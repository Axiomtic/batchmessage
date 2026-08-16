package com.local.bulksms.ui.data

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.EditableTable
import com.local.bulksms.ui.send.SendFlowUiState

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

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
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use(callbacks.onXlsxImport) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("数据", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("导入数据", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ImportCard(
                label = "文件",
                hint = "Excel .xlsx",
                modifier = Modifier.weight(1f).testTag("import-file"),
                onClick = { fileLauncher.launch(arrayOf(XLSX_MIME)) },
            )
            ImportCard(
                label = "剪贴板",
                hint = "粘贴表格数据",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("数据表格", style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.table?.rows?.size ?: 0} 行 · ${state.table?.columns?.size ?: 0} 列",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.table?.let { table ->
            EditableTable(
                table = table,
                onCellChanged = { callbacks.onCellChanged(it.rowId, it.columnIndex, it.value) },
                onAddRow = callbacks.onAddRow,
                onAddColumn = callbacks.onAddColumn,
                onDeleteRowRequested = { rowId ->
                    val ordinal = table.rows.indexOfFirst { it.id == rowId } + 1
                    deleteTarget = DeleteTarget.Row(rowId, ordinal)
                },
                onDeleteColumnRequested = { index ->
                    deleteTarget = DeleteTarget.Column(index, table.columns[index].name)
                },
                modifier = Modifier.weight(1f),
            )
        }

        state.blockingError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = callbacks.onCancelImport,
            title = { Text("覆盖现有数据？") },
            text = { Text("导入会替换当前表格；已取消同步的短信会保持不变。") },
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

@Composable
private fun ImportCard(
    label: String,
    hint: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = modifier.height(84.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.labelSmall)
        }
    }
}
