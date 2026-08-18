package com.local.bulksms.ui.send

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberScrollable2DState
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.PhoneAvailability
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.ui.theme.availablePhoneColor
import com.local.bulksms.ui.theme.emptyPhoneColor
import com.local.bulksms.ui.theme.invalidPhoneColor

data class CellEdit(val rowId: Long, val columnIndex: Int, val value: String)

data class HeaderEdit(val columnIndex: Int, val value: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableTable(
    table: ImportedTable,
    onCellChanged: (CellEdit) -> Unit,
    onHeaderChanged: (HeaderEdit) -> Unit = {},
    onColumnHeaderClicked: (Int) -> Unit = {},
    onAddRow: () -> Unit = {},
    onAddColumn: () -> Unit = {},
    onDeleteRowRequested: (Long) -> Unit = {},
    onDeleteColumnRequested: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val twoDimensionalScroll = rememberScrollable2DState { delta ->
        val consumedX = horizontalScroll.dispatchRawDelta(-delta.x)
        val consumedY = verticalScroll.dispatchRawDelta(-delta.y)
        Offset(-consumedX, -consumedY)
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    val columnWidths = table.columns.mapIndexed { index, column ->
        contentAwareColumnWidth(
            listOf(column.name) + table.rows.map { it.cells.getOrNull(index).orEmpty() },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .scrollable2D(twoDimensionalScroll)
            .testTag("editable-table-2d"),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(horizontalScroll, enabled = false)
                .verticalScroll(verticalScroll, enabled = false),
        ) {
            Row {
                AxisCell("", 36.dp, headerColor)
                table.columns.forEachIndexed { index, column ->
                    val (background, textColor) = columnHeaderColors(
                        index = index,
                        phoneColumnIndex = table.phoneColumnIndex,
                        backupPhoneColumnIndex = table.backupPhoneColumnIndex,
                    )
                    AxisCell(
                        text = column.name,
                        width = columnWidths[index],
                        background = background,
                        textColor = textColor,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onColumnHeaderClicked(index) },
                                onLongClick = { onDeleteColumnRequested(index) },
                            )
                            .testTag("column-label-${column.name}"),
                    )
                }
                EdgeAddButton(
                    contentDescription = "添加列",
                    testTag = "add-column",
                    onClick = onAddColumn,
                )
            }

            table.rows.forEachIndexed { rowIndex, row ->
                Row {
                    AxisCell(
                        text = (rowIndex + 1).toString(),
                        width = 36.dp,
                        background = headerColor,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { onDeleteRowRequested(row.id) },
                            )
                            .testTag("row-label-$rowIndex"),
                    )
                    table.columns.indices.forEach { columnIndex ->
                        val value = row.cells.getOrNull(columnIndex).orEmpty()
                        val isPhoneColumn = columnIndex == table.phoneColumnIndex ||
                            columnIndex == table.backupPhoneColumnIndex
                        val textColor = if (isPhoneColumn) {
                            phoneCellColor(PhoneNumberChecker.availability(value))
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { newValue ->
                                onCellChanged(CellEdit(row.id, columnIndex, newValue))
                            },
                            modifier = Modifier
                                .width(columnWidths[columnIndex])
                                .height(38.dp)
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 8.dp, vertical = 9.dp)
                                .testTag("cell-${row.id}-$columnIndex"),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
                        )
                    }
                    Box(Modifier.size(40.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeAddButton(
                    contentDescription = "添加行",
                    testTag = "add-row",
                    onClick = onAddRow,
                )
                Box(
                    Modifier
                        .width(columnWidths.fold(0.dp) { total, width -> total + width })
                        .height(40.dp)
                        .border(0.5.dp, borderColor),
                )
            }
        }
    }
}

/**
 * Header colors signal the phone-column role: both roles use the app's teal-green
 * theme colors, the primary role with a solid fill and the backup with a light one.
 */
@Composable
private fun columnHeaderColors(
    index: Int,
    phoneColumnIndex: Int?,
    backupPhoneColumnIndex: Int?,
): Pair<Color, Color> = when (index) {
    phoneColumnIndex -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    backupPhoneColumnIndex -> {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
}

@Composable
private fun phoneCellColor(availability: PhoneAvailability): Color = when (availability) {
    PhoneAvailability.AVAILABLE -> availablePhoneColor()
    PhoneAvailability.INVALID -> invalidPhoneColor()
    PhoneAvailability.EMPTY -> emptyPhoneColor()
}

@Composable
private fun AxisCell(
    text: String,
    width: Dp,
    background: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(38.dp)
            .background(background)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun EdgeAddButton(
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun contentAwareColumnWidth(values: List<String>): Dp {
    val longestUnits = values.maxOfOrNull { value ->
        value.sumOf { character -> if (character.code > 0xff) 2 else 1 }
    } ?: 1
    return (longestUnits * 8f + 28f).dp.coerceIn(76.dp, 240.dp)
}
