package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.bulksms.data.TemplateEntity
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.template.TemplateUiState
import org.junit.Rule
import org.junit.Test

class SmsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val template = TemplateEntity("default", "服务到期提醒", "您好，{A}")

    @Test
    fun templateActionsUseInlineAddDeleteAndDirtyGatedSave() {
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = SendFlowViewModel().state.value,
                    templateState = TemplateUiState(
                        templates = listOf(template),
                        selectedTemplateId = template.id,
                        editorName = template.name,
                        editorBody = template.body,
                        savedBody = template.body,
                    ),
                    callbacks = BulkSmsCallbacks(),
                )
            }
        }

        composeRule.onNodeWithTag("template-add").performClick()
        composeRule.onNodeWithText("添加模板").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("template-delete").assertIsNotEnabled()
        composeRule.onNodeWithTag("template-save").assertIsNotEnabled()
        composeRule.onNodeWithText("另存为").assertDoesNotExist()
    }

    @Test
    fun saveEnablesOnlyForChangedBody() {
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = SendFlowViewModel().state.value,
                    templateState = TemplateUiState(
                        templates = listOf(template),
                        selectedTemplateId = template.id,
                        editorName = template.name,
                        editorBody = "您好，{A}，内容已修改",
                        savedBody = template.body,
                    ),
                    callbacks = BulkSmsCallbacks(),
                )
            }
        }

        composeRule.onNodeWithTag("template-save").assertIsEnabled()
        composeRule.onNodeWithText("实时同步").assertDoesNotExist()
    }
}
