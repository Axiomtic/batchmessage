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
import androidx.compose.ui.unit.Dp
import com.local.bulksms.model.ImportedTable

data class CellEdit(
    val rowId: Long,
    val columnIndex: Int,
    val value: String,
)

data class HeaderEdit(
    val columnIndex: Int,
    val value: String,
)

@Composable
fun EditableTable(
    table: ImportedTable,
    onCellChanged: (CellEdit) -> Unit,
    onHeaderChanged: (HeaderEdit) -> Unit = {},
    onPhoneColumnSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val columnWidths = table.columns.mapIndexed { columnIndex, column ->
        contentAwareColumnWidth(
            listOf(column.name) + table.rows.map { row -> row.cells.getOrNull(columnIndex).orEmpty() },
        )
    }

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
                    Column(
                        modifier = Modifier
                            .width(columnWidths[index])
                            .heightIn(min = 56.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    ) {
                        BasicTextField(
                            value = column.name,
                            onValueChange = { onHeaderChanged(HeaderEdit(index, it)) },
                            modifier = Modifier
                                .width(columnWidths[index] - 16.dp)
                                .testTag("header-$index-editor"),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        Text(
                            text = if (selected) "已选手机号列" else "点击设为手机号列",
                            modifier = Modifier
                                .width(columnWidths[index] - 16.dp)
                                .clickable { onPhoneColumnSelected(index) }
                                .testTag("column-header-$index"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
                                .width(columnWidths[columnIndex])
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

internal fun contentAwareColumnWidth(values: List<String>): Dp {
    val longestUnits = values.maxOfOrNull { value ->
        value.sumOf { character -> if (character.code > 0xff) 2 else 1 }
    } ?: 1
    return (longestUnits * 7.2f + 24f).dp.coerceIn(76.dp, 240.dp)
}
