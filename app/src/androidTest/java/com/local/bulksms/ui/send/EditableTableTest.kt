package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditableTableTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun horizontalSwipeScrollsColumns() {
        val table = ImportedTable(
            columns = (0 until 8).map { DynamicColumn(it, "列${it + 1}") },
            rows = (0L until 5L).map { rowId ->
                DynamicRow(rowId, List(8) { column -> "$rowId-$column" })
            },
            firstRowIsHeader = false,
        )

        composeRule.setContent {
            MaterialTheme {
                EditableTable(table, onCellChanged = {}, onColumnHeaderClicked = {})
            }
        }

        val cell = composeRule.onNodeWithTag("cell-0-0")
        val before = cell.fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("editable-table-2d").performTouchInput {
            swipe(
                start = Offset(width * 0.8f, height * 0.5f),
                end = Offset(width * 0.2f, height * 0.5f),
                durationMillis = 500,
            )
        }
        val after = cell.fetchSemanticsNode().boundsInRoot

        assertTrue(after.left < before.left)
    }

    @Test
    fun headerSwitchReportsUserCorrection() {
        val table = ImportedTable(
            columns = listOf(DynamicColumn(0, "手机号")),
            rows = listOf(DynamicRow(0L, listOf("13800138000"))),
            firstRowIsHeader = true,
        )
        var firstRowIsHeader: Boolean? = null

        composeRule.setContent {
            MaterialTheme {
                ImportScreen(
                    state = SendFlowUiState(table = table),
                    onClipboardImported = {},
                    onXlsxImported = {},
                    onHeaderChanged = { firstRowIsHeader = it },
                    onCellChanged = {},
                    onPhoneColumnSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("header-switch").performClick()

        assertEquals(false, firstRowIsHeader)
    }

    @Test
    fun cellIsEditableAndColumnLabelsUseLetters() {
        var table by mutableStateOf(ImportedTable(
            columns = listOf(DynamicColumn(0, "A"), DynamicColumn(1, "B")),
            rows = listOf(DynamicRow(7L, listOf("13800138000", "张三"))),
            firstRowIsHeader = true,
        ))
        var lastEdit: CellEdit? = null

        composeRule.setContent {
            MaterialTheme {
                EditableTable(
                    table = table,
                    onCellChanged = { edit ->
                        lastEdit = edit
                        table = table.copy(rows = table.rows.map { row ->
                            if (row.id != edit.rowId) row else row.copy(
                                cells = row.cells.toMutableList().also { cells ->
                                    cells[edit.columnIndex] = edit.value
                                },
                            )
                        })
                    },
                )
            }
        }

        composeRule.onNodeWithTag("cell-7-1").performTextReplacement("张三丰")
        composeRule.onNodeWithTag("column-label-B").assertExists()

        assertEquals(CellEdit(7L, 1, "张三丰"), lastEdit)
    }
}
