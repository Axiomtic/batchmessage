package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditableTableTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tableExposesHorizontalAndVerticalScrolling() {
        val table = ImportedTable(
            columns = (0 until 8).map { DynamicColumn(it, "列${it + 1}") },
            rows = (0L until 30L).map { rowId ->
                DynamicRow(rowId, List(8) { column -> "$rowId-$column" })
            },
            firstRowIsHeader = false,
        )

        composeRule.setContent {
            MaterialTheme {
                EditableTable(table, onCellChanged = {}, onPhoneColumnSelected = {})
            }
        }

        composeRule.onNodeWithTag("editable-table-horizontal").assert(hasScrollAction())
        composeRule.onNodeWithTag("editable-table-vertical").assert(hasScrollAction())
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
    fun cellIsEditableAndColumnHeaderSelectsPhoneColumn() {
        var table by mutableStateOf(ImportedTable(
            columns = listOf(DynamicColumn(0, "手机号"), DynamicColumn(1, "姓名")),
            rows = listOf(DynamicRow(7L, listOf("13800138000", "张三"))),
            firstRowIsHeader = true,
        ))
        var lastEdit: CellEdit? = null
        var selectedPhoneColumn: Int? = null

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
                    onPhoneColumnSelected = { selectedPhoneColumn = it },
                )
            }
        }

        composeRule.onNodeWithTag("cell-7-1").performTextReplacement("张三丰")
        composeRule.onNodeWithTag("column-header-1").performClick()

        assertEquals(CellEdit(7L, 1, "张三丰"), lastEdit)
        assertEquals(1, selectedPhoneColumn)
    }

    @Test
    fun headerTextCanBeEditedWithoutLosingPhoneColumnSelection() {
        val table = ImportedTable(
            columns = listOf(DynamicColumn(0, "姓名"), DynamicColumn(1, "电话")),
            rows = listOf(DynamicRow(0L, listOf("张三", "13800138000"))),
            firstRowIsHeader = true,
            phoneColumnIndex = 1,
        )
        var headerEdit: HeaderEdit? = null

        composeRule.setContent {
            MaterialTheme {
                EditableTable(
                    table = table,
                    onCellChanged = {},
                    onHeaderChanged = { headerEdit = it },
                    onPhoneColumnSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("header-0-editor").performTextReplacement("客户姓名")

        assertEquals(HeaderEdit(0, "客户姓名"), headerEdit)
    }
}
