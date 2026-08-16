package com.local.bulksms.ui.send

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.bulksms.model.ImportedTable

data class CellEdit(
    val rowId: Long,
    val columnIndex: Int,
    val value: String,
)

@Composable
fun EditableTable(
    table: ImportedTable,
    onCellChanged: (CellEdit) -> Unit,
    onPhoneColumnSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
            .testTag("editable-table-horizontal"),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(verticalScroll)
                .testTag("editable-table-vertical"),
        ) {
            Row {
                table.columns.forEachIndexed { index, column ->
                    val selected = index == table.phoneColumnIndex
                    Box(
                        modifier = Modifier
                            .width(CELL_WIDTH)
                            .heightIn(min = 48.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .border(0.5.dp, borderColor)
                            .clickable { onPhoneColumnSelected(index) }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                            .testTag("column-header-$index"),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (selected) "手机号 · ${column.name}" else column.name,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            table.rows.forEach { row ->
                Row {
                    table.columns.indices.forEach { columnIndex ->
                        BasicTextField(
                            value = row.cells.getOrNull(columnIndex).orEmpty(),
                            onValueChange = { value ->
                                onCellChanged(CellEdit(row.id, columnIndex, value))
                            },
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .heightIn(min = 46.dp)
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                                .testTag("cell-${row.id}-$columnIndex"),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val CELL_WIDTH = 160.dp
