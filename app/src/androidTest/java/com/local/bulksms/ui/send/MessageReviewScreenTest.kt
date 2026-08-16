package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.template.DraftSynchronizer
import org.junit.Rule
import org.junit.Test

class MessageReviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingInBodyUnchecksSyncWithoutAddingNameCard() {
        val original = MessageDraft(
            rowId = 7L,
            phoneNumber = "13800138000",
            generatedBody = "张三您好，金额120",
            currentBody = "张三您好，金额120",
            columnNames = listOf("手机号", "姓名", "金额"),
            phoneColumnIndex = 0,
        )
        var state by mutableStateOf(SendFlowUiState(drafts = listOf(original)))

        composeRule.setContent {
            MaterialTheme {
                MessageReviewScreen(
                    state = state,
                    onEdit = { rowId, body ->
                        state = state.copy(drafts = state.drafts.map { draft ->
                            if (draft.rowId == rowId) DraftSynchronizer.editBody(draft, body) else draft
                        })
                    },
                    onSyncChanged = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("message-body-7").performTextReplacement("张三您好，已延期")

        composeRule.onNodeWithContentDescription("与表同步").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off),
        )
        composeRule.onNodeWithTag("recipient-title-card").assertDoesNotExist()
    }
}
