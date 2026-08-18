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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onPhoneColumnSelected: (Int) -> Unit = {},
    onBackupPhoneColumnSelected: (Int) -> Unit = {},
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
    var columnMenuIndex by remember { mutableStateOf<Int?>(null) }
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
                    val badge = when (index) {
                        table.phoneColumnIndex -> "主"
                        table.backupPhoneColumnIndex -> "备"
                        else -> null
                    }
                    AxisCell(
                        text = column.name,
                        width = columnWidths[index],
                        background = headerColor,
                        badge = badge,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { columnMenuIndex = index },
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

        columnMenuIndex?.let { menuIndex ->
            val column = table.columns.getOrNull(menuIndex) ?: return@let
            DropdownMenu(
                expanded = true,
                onDismissRequest = { columnMenuIndex = null },
                modifier = Modifier.testTag("column-menu"),
            ) {
                Text(
                    "列 ${column.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                DropdownMenuItem(
                    text = { Text(if (menuIndex == table.phoneColumnIndex) "✓ 主电话号码列" else "设为主电话号码列") },
                    onClick = {
                        columnMenuIndex = null
                        onPhoneColumnSelected(menuIndex)
                    },
                    modifier = Modifier.testTag("column-menu-phone"),
                )
                DropdownMenuItem(
                    text = { Text(if (menuIndex == table.backupPhoneColumnIndex) "✓ 备用电话号码列" else "设为备用电话号码列") },
                    onClick = {
                        columnMenuIndex = null
                        onBackupPhoneColumnSelected(menuIndex)
                    },
                    modifier = Modifier.testTag("column-menu-backup-phone"),
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("删除该列", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        columnMenuIndex = null
                        onDeleteColumnRequested(menuIndex)
                    },
                    modifier = Modifier.testTag("column-menu-delete"),
                )
            }
        }
    }
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
    badge: String? = null,
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
        )
        badge?.let { value ->
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 3.dp),
            )
        }
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
