package com.local.bulksms.ui.data

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DataScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataPageUsesImportCardsAndEdgeAddButtons() {
        var addedRows = 0
        var addedColumns = 0
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                DataScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onAddRow = { addedRows++ },
                        onAddColumn = { addedColumns++ },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("import-file").assertExists()
        composeRule.onNodeWithTag("import-clipboard").assertExists()
        composeRule.onNodeWithTag("add-column").performClick()
        composeRule.onNodeWithTag("add-row").performClick()
        assertEquals(1, addedColumns)
        assertEquals(1, addedRows)
        composeRule.onNodeWithText("数据").assertDoesNotExist()
        composeRule.onNodeWithText("+ 行").assertDoesNotExist()
    }

    @Test
    fun longPressingLabelsRequestsSpecificDeletion() {
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                DataScreen(state, BulkSmsCallbacks())
            }
        }

        composeRule.onNodeWithTag("row-label-1").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除第 2 行？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("column-label-电话").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除第 电话 列？").assertExists()
    }

    @Test
    fun clickingColumnHeaderCyclesPhoneRoles() {
        var clicked: Int? = null
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                DataScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onColumnHeaderClicked = { clicked = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("column-label-服务到期日期").performClick()
        composeRule.runOnIdle { assertEquals(2, clicked) }
    }
}
