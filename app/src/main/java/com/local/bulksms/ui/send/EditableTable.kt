package com.local.bulksms.ui.send

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.PhoneAvailability
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.ui.theme.availablePhoneColor
import com.local.bulksms.ui.theme.emptyPhoneColor
import com.local.bulksms.ui.theme.invalidPhoneColor

data class CellEdit(val rowId: Long, val columnIndex: Int, val value: String)

data class HeaderEdit(val columnIndex: Int, val value: String)

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
    showAvailable: Boolean = true,
    showUnavailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    var editingCell by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editingCell) {
        if (editingCell != null) focusRequester.requestFocus()
    }
    val columnWidths = remember(table) {
        table.columns.mapIndexed { index, column ->
            contentAwareColumnWidth(
                listOf(column.name) + table.rows.map { it.cells.getOrNull(index).orEmpty() },
            )
        }
    }
    val columnsWidth = columnWidths.fold(0.dp) { total, width -> total + width }
    val contentWidth = 36.dp + columnsWidth + 40.dp
    // With both filters on (the default) every row is visible, so skip the per-row
    // phone-number scan entirely; this keeps typing in a large sheet smooth.
    val visibleRows = remember(table, showAvailable, showUnavailable) {
        if (showAvailable && showUnavailable) {
            table.rows
        } else {
            table.rows.filter { row ->
                isRowVisible(row, table, showAvailable, showUnavailable)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag("editable-table-2d"),
    ) {
        Column(
            modifier = Modifier
                .width(contentWidth)
                .fillMaxHeight()
                .horizontalScroll(horizontalScroll),
        ) {
            // Header row, pinned above the lazily rendered data rows.
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

            // Data rows, vertically lazy so a 1000-row sheet only composes the visible rows.
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(visibleRows, key = { it.id }) { row ->
                    Row {
                        AxisCell(
                            text = (row.id + 1).toString(),
                            width = 36.dp,
                            background = headerColor,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { onDeleteRowRequested(row.id) },
                                )
                                .testTag("row-label-${row.id}"),
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
                            val cellModifier = Modifier
                                .width(columnWidths[columnIndex])
                                .height(38.dp)
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 8.dp, vertical = 9.dp)
                            if (editingCell == (row.id to columnIndex)) {
                                BasicTextField(
                                    value = value,
                                    onValueChange = { newValue ->
                                        onCellChanged(CellEdit(row.id, columnIndex, newValue))
                                    },
                                    modifier = cellModifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { focusState ->
                                            if (!focusState.isFocused) editingCell = null
                                        }
                                        .testTag("cell-${row.id}-$columnIndex"),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
                                )
                            } else {
                                val hidden = isPhoneColumn && !(showAvailable && showUnavailable) &&
                                    cellHasHiddenNumbers(value, showAvailable, showUnavailable)
                                Box(
                                    modifier = cellModifier
                                        .clickable { editingCell = row.id to columnIndex }
                                        .then(if (hidden) Modifier.alpha(0.3f) else Modifier)
                                        .testTag("cell-${row.id}-$columnIndex"),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Box(Modifier.size(40.dp))
                    }
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
                        .width(columnsWidth)
                        .height(40.dp)
                        .border(0.5.dp, borderColor),
                )
            }
        }
    }
}

/**
 * Header colors signal the phone-column role: both roles use a green family so they
 * read as phone columns; the primary role gets the app's teal-green primary fill and
 * the backup a clearly light-green fill (not a cyan/teal tint).
 */
@Composable
private fun columnHeaderColors(
    index: Int,
    phoneColumnIndex: Int?,
    backupPhoneColumnIndex: Int?,
): Pair<Color, Color> = when (index) {
    phoneColumnIndex -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    backupPhoneColumnIndex -> PhoneColumnPalette.BackupHeader to PhoneColumnPalette.OnBackupHeader
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
}

/** Light-green backup header colors, distinct from the teal-cyan primary. */
private object PhoneColumnPalette {
    val BackupHeader = Color(0xFFC8E6C9)
    val OnBackupHeader = Color(0xFF1B5E20)
}

@Composable
private fun phoneCellColor(availability: PhoneAvailability): Color = when (availability) {
    PhoneAvailability.AVAILABLE -> availablePhoneColor()
    PhoneAvailability.INVALID -> invalidPhoneColor()
    PhoneAvailability.EMPTY -> emptyPhoneColor()
}

/**
 * Row visibility driven by the available/unavailable phone filters. A row without any
 * phone content stays visible only when both filters are on.
 */
private fun isRowVisible(
    row: DynamicRow,
    table: ImportedTable,
    showAvailable: Boolean,
    showUnavailable: Boolean,
): Boolean {
    val indexes = listOfNotNull(table.phoneColumnIndex, table.backupPhoneColumnIndex)
    val cells = indexes.mapNotNull { row.cells.getOrNull(it) }
    if (cells.all(String::isBlank)) return showAvailable && showUnavailable

    val hasAvailable = cells.any { cell ->
        PhoneNumberChecker.extractMobileNumbers(cell).any {
            PhoneNumberChecker.availability(it) == PhoneAvailability.AVAILABLE
        }
    }
    val hasUnavailable = cells.any { cell ->
        val numbers = PhoneNumberChecker.extractMobileNumbers(cell)
        numbers.isEmpty() || numbers.any {
            PhoneNumberChecker.availability(it) != PhoneAvailability.AVAILABLE
        }
    }
    return (hasAvailable && showAvailable) || (hasUnavailable && showUnavailable)
}

/**
 * True when the cell contains at least one number of a category that is currently
 * hidden, so the whole cell can be faded out.
 */
private fun cellHasHiddenNumbers(
    value: String,
    showAvailable: Boolean,
    showUnavailable: Boolean,
): Boolean {
    return PhoneNumberChecker.extractMobileNumbers(value).any { number ->
        val isAvailable = PhoneNumberChecker.availability(number) == PhoneAvailability.AVAILABLE
        (isAvailable && !showAvailable) || (!isAvailable && !showUnavailable)
    }
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
