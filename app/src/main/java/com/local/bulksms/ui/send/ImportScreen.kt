package com.local.bulksms.ui.send

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.InputStream

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ImportScreen(
    state: SendFlowUiState,
    onClipboardImported: (String) -> Unit,
    onXlsxImported: (InputStream) -> Unit,
    onHeaderChanged: (Boolean) -> Unit,
    onCellChanged: (CellEdit) -> Unit,
    onPhoneColumnSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use(onXlsxImported)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("导入数据", style = MaterialTheme.typography.headlineSmall)
        Text(
            "从 Excel 或剪贴板导入，最多 100 条。导入后可直接编辑表格。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { fileLauncher.launch(arrayOf(XLSX_MIME)) }) {
                Text("选择 Excel")
            }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    onClipboardImported(text)
                },
            ) {
                Text("从剪贴板导入")
            }
        }

        state.blockingError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        state.importWarnings.forEach { warning ->
            Text(warning, color = MaterialTheme.colorScheme.tertiary)
        }

        state.table?.let { table ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("第一行是字段名", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "自动判断：${if (state.detectedHeader) "是" else "否"}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Switch(
                    checked = table.firstRowIsHeader,
                    onCheckedChange = onHeaderChanged,
                    modifier = Modifier.testTag("header-switch"),
                )
            }
            Text(
                if (state.selectedPhoneColumn == null) {
                    "请点击列头选择手机号列"
                } else {
                    "手机号列：${table.columns[state.selectedPhoneColumn].name}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state.selectedPhoneColumn == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            EditableTable(
                table = table,
                onCellChanged = onCellChanged,
                onPhoneColumnSelected = onPhoneColumnSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
