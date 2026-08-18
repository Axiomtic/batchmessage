package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.bulksms.model.MessageDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageReviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val first = MessageDraft(
        rowId = 7L,
        phoneNumber = "13800138000",
        generatedBody = "张三您好，金额120",
        currentBody = "张三您好，金额120",
        columnNames = listOf("手机号", "姓名", "金额"),
        phoneColumnIndex = 0,
    )

    @Test
    fun previewShowsFullPhoneAndReadOnlyBodyWithSendCheckbox() {
        var selectionChange: Pair<Long, Boolean>? = null
        composeRule.setContent {
            MaterialTheme {
                MessageReviewList(
                    drafts = listOf(first),
                    selectedRowIds = setOf(first.rowId),
                    enabled = true,
                    onSelectionChanged = { rowId, selected -> selectionChange = rowId to selected },
                )
            }
        }

        composeRule.onNodeWithText("1 · 主:13800138000").assertExists()
        composeRule.onNodeWithText("138****8000").assertDoesNotExist()
        composeRule.onNodeWithText("张三您好，金额120").assertExists()
        composeRule.onNodeWithTag("message-body-7").assertDoesNotExist()
        composeRule.onNodeWithTag("send-draft-7").performClick()
        assertEquals(7L to false, selectionChange)
    }

    @Test
    fun previewShowsBackupNumberWhenPresent() {
        val draft = first.copy(
            backupPhoneNumber = "13900139000",
            backupPhoneColumnIndex = 1,
        )
        composeRule.setContent {
            MaterialTheme {
                MessageReviewList(
                    drafts = listOf(draft),
                    selectedRowIds = emptySet(),
                    enabled = true,
                    onSelectionChanged = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("1 · 主:13800138000").assertExists()
        composeRule.onNodeWithText("备:13900139000").assertExists()
    }

    @Test
    fun selectAllCheckboxShowsIndeterminateAndSelectsEverything() {
        val second = first.copy(rowId = 8L, phoneNumber = "13900139000")
        var selectAll: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                MessageReviewHeader(
                    drafts = listOf(first, second),
                    selectedRowIds = setOf(first.rowId),
                    enabled = true,
                    onSelectAll = { selectAll = it },
                )
            }
        }

        composeRule.onNodeWithTag("select-all-drafts").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ToggleableState,
                ToggleableState.Indeterminate,
            ),
        )
        composeRule.onNodeWithTag("select-all-drafts").performClick()
        assertEquals(true, selectAll)
    }
}
