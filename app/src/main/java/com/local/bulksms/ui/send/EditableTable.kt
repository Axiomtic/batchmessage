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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.FilterMatcher
import com.local.bulksms.importdata.PhoneAvailability
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.model.ColumnFilter
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.ui.theme.availablePhoneColor
import com.local.bulksms.ui.theme.emptyPhoneColor
import com.local.bulksms.ui.theme.invalidPhoneColor

data class CellEdit(val rowId: Long, val columnIndex: Int, val value: String)

data class HeaderEdit(val columnIndex: Int, val value: String)

/** Precomputed display facts for one phone-column cell. */
private data class PhoneCellInfo(
    val availability: PhoneAvailability,
    val hidden: Boolean,
)

/**
 * Precomputed per-table phone facts: cell-level display info plus the set of rows
 * that have at least one visible number. Rows without any visible phone number are
 * filtered out of the table (a filter), while cells containing hidden numbers stay
 * in the list but are faded.
 */
private data class PhoneTableState(
    val cellInfo: Map<Pair<Long, Int>, PhoneCellInfo>,
    val visibleRowIds: Set<Long>?,
)

@Composable
fun EditableTable(
    table: ImportedTable,
    onCellChanged: (CellEdit) -> Unit,
    onHeaderChanged: (HeaderEdit) -> Unit = {},
    onColumnHeaderClicked: (Int) -> Unit = {},
    onFilterColumnClicked: (Int) -> Unit = {},
    onAddRow: () -> Unit = {},
    onAddColumn: () -> Unit = {},
    onDeleteRowRequested: (Long) -> Unit = {},
    onDeleteColumnRequested: (Int) -> Unit = {},
    showAvailable: Boolean = true,
    showUnavailable: Boolean = true,
    columnFilters: List<ColumnFilter> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    var editingCell by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val columnWidths = remember(table) {
        table.columns.mapIndexed { index, column ->
            contentAwareColumnWidth(
                listOf(column.name) + table.rows.map { it.cells.getOrNull(index).orEmpty() },
            )
        }
    }
    val columnsWidth = columnWidths.fold(0.dp) { total, width -> total + width }
    val contentWidth = 36.dp + columnsWidth + 40.dp

    // Precompute the phone-column facts once per table so scrolling never runs
    // regexes per visible cell: cell availability/hidden for fading, plus which
    // rows keep at least one visible number (rows without any are filtered out).
    val phoneState = remember(table, showAvailable, showUnavailable) {
        val phoneIndexes = listOfNotNull(table.phoneColumnIndex, table.backupPhoneColumnIndex)
        if (phoneIndexes.isEmpty()) {
            // No phone column selected: nothing to filter, nothing to fade.
            PhoneTableState(cellInfo = emptyMap(), visibleRowIds = null)
        } else {
            val cellInfo = mutableMapOf<Pair<Long, Int>, PhoneCellInfo>()
            val visibleRowIds = mutableSetOf<Long>()
            for (row in table.rows) {
                var hasAnyNumber = false
                var hasVisible = false
                var hasHidden = false
                for (index in phoneIndexes) {
                    val value = row.cells.getOrNull(index).orEmpty()
                    val numbers = PhoneNumberChecker.extractPhoneNumbers(value)
                    if (numbers.isNotEmpty()) hasAnyNumber = true
                    if (numbers.any { PhoneNumberChecker.isVisible(it, showAvailable, showUnavailable) }) {
                        hasVisible = true
                    }
                    if (numbers.any { !PhoneNumberChecker.isVisible(it, showAvailable, showUnavailable) }) {
                        hasHidden = true
                    }
                    cellInfo[row.id to index] = PhoneCellInfo(
                        availability = PhoneNumberChecker.availability(value),
                        hidden = hasHidden,
                    )
                }
                // Filtering hides rows whose numbers are ALL hidden. Rows without any
                // number count as the empty/unavailable category and follow the
                // unavailable toggle (visible by default, hidden when it is off).
                if (hasVisible || (!hasAnyNumber && showUnavailable)) visibleRowIds += row.id
            }
            PhoneTableState(cellInfo = cellInfo, visibleRowIds = visibleRowIds)
        }
    }
    // Rows with no visible phone number at all are hidden, like a filter; then the
    // per-column string filters are applied on top (a row must satisfy every column).
    val visibleRows = table.rows.filter { row ->
        val phoneOk = phoneState.visibleRowIds?.let { ids -> row.id in ids } ?: true
        phoneOk && columnFilters.all { filter ->
            FilterMatcher.matches(row.cells.getOrNull(filter.columnIndex).orEmpty(), filter)
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val bodySmall = MaterialTheme.typography.bodySmall

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
                    val hasFilter = columnFilters.any { it.columnIndex == index && it.activeConditions.isNotEmpty() }
                    // Column header: tapping the name cycles the phone-column role;
                    // the funnel icon on the right opens the column filter dialog.
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
                            .height(38.dp)
                            .background(background)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .combinedClickable(
                                onClick = { onColumnHeaderClicked(index) },
                                onLongClick = { onDeleteColumnRequested(index) },
                            )
                            .testTag("column-label-${column.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = column.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                painter = painterResource(com.local.bulksms.R.drawable.ic_filter),
                                contentDescription = "筛选${column.name}",
                                tint = if (hasFilter) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onFilterColumnClicked(index) }
                                    .testTag("column-filter-$index"),
                            )
                        }
                    }
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
                    // One grid per row drawn once instead of a border modifier on
                    // every cell: far fewer draw calls while scrolling.
                    Row(
                        modifier = Modifier.drawBehind {
                            val lineWidth = 0.5.dp.toPx()
                            val bottom = 38.dp.toPx()
                            var x = 36.dp.toPx()
                            for (width in columnWidths) {
                                x += width.toPx()
                                drawLine(
                                    color = borderColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, bottom),
                                    strokeWidth = lineWidth,
                                )
                            }
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, bottom),
                                end = Offset(x + 40.dp.toPx(), bottom),
                                strokeWidth = lineWidth,
                            )
                        },
                    ) {
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
                            val info = phoneState.cellInfo[row.id to columnIndex]
                            val textColor = if (info != null) {
                                phoneCellColor(info.availability)
                            } else {
                                onSurface
                            }
                            val cellModifier = Modifier
                                .width(columnWidths[columnIndex])
                                .height(38.dp)
                                .padding(horizontal = 8.dp, vertical = 9.dp)
                            if (editingCell == (row.id to columnIndex)) {
                                val focusRequester = remember { FocusRequester() }
                                // onFocusChanged fires once right when the field enters
                                // composition with the "not focused" state; clearing the
                                // edit cell on that first callback would instantly undo
                                // the tap. Only clear after the field actually had focus.
                                var hasFocus by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    // The field is only attached after the first
                                    // frame; requesting focus earlier silently fails
                                    // and the cell would snap back to read-only.
                                    withFrameNanos { }
                                    focusRequester.requestFocus()
                                }
                                BasicTextField(
                                    value = value,
                                    onValueChange = { newValue ->
                                        onCellChanged(CellEdit(row.id, columnIndex, newValue))
                                    },
                                    modifier = cellModifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { focusState ->
                                            if (hasFocus && !focusState.isFocused) editingCell = null
                                            hasFocus = focusState.isFocused
                                        }
                                        .testTag("cell-${row.id}-$columnIndex"),
                                    singleLine = true,
                                    textStyle = bodySmall.copy(color = textColor),
                                )
                            } else {
                                // Fade hidden numbers through the text color instead of
                                // Modifier.alpha, which would force an offscreen layer.
                                val hidden = info?.hidden == true
                                Box(
                                    modifier = cellModifier
                                        .clickable { editingCell = row.id to columnIndex }
                                        .testTag("cell-${row.id}-$columnIndex"),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Text(
                                        value,
                                        style = bodySmall,
                                        color = if (hidden) textColor.copy(alpha = 0.3f) else textColor,
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
