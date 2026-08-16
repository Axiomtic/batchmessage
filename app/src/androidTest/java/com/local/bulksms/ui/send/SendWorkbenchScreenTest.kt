package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.bulksms.model.RawTable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SendWorkbenchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun workbenchShowsEverySectionWithoutOuterScrolling() {
        val state = SendFlowViewModel().state.value

        composeRule.setContent {
            MaterialTheme {
                SendWorkbenchScreen(state = state, callbacks = SendWorkbenchCallbacks())
            }
        }

        composeRule.onNodeWithTag("send-workbench")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("数据表格").assertExists()
        composeRule.onNodeWithText("现场模板").assertExists()
        composeRule.onNodeWithText("待发送短信", substring = true).assertExists()
        composeRule.onNodeWithText("确认并发送").assertExists()
        composeRule.onNodeWithText("模板管理").assertExists()
    }

    @Test
    fun pendingImportRequiresExplicitOverwriteOrCancel() {
        var cancelled = false
        var confirmed = false
        val state = SendFlowViewModel().state.value.copy(
            pendingImport = PendingImport(
                rawTable = RawTable(listOf(listOf("名字", "电话"), listOf("王五", "13700137000"))),
                detectedHeader = true,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                SendWorkbenchScreen(
                    state = state,
                    callbacks = SendWorkbenchCallbacks(
                        onCancelImport = { cancelled = true },
                        onConfirmImport = { confirmed = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("覆盖现有数据？").assertExists()
        composeRule.onNodeWithText("取消").performClick()

        assertTrue(cancelled)
        assertTrue(!confirmed)
    }
}
