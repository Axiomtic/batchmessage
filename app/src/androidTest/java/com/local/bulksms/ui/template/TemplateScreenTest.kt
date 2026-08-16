package com.local.bulksms.ui.template

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TemplateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun variableButtonInsertsTokenAtCursor() {
        var body by mutableStateOf("您好")

        composeRule.setContent {
            MaterialTheme {
                TemplateScreen(
                    state = TemplateUiState(editorName = "通知", editorBody = body),
                    availableVariables = listOf("姓名"),
                    onSelect = {},
                    onStartNew = {},
                    onNameChanged = {},
                    onBodyChanged = { body = it },
                    onOverwrite = {},
                    onSaveAs = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("variable-姓名").performClick()

        assertEquals("您好{姓名}", body)
    }

    @Test
    fun overwriteAndSaveAsAreSeparateActions() {
        var overwriteCount = 0
        var savedAsName: String? = null

        composeRule.setContent {
            MaterialTheme {
                TemplateScreen(
                    state = TemplateUiState(
                        selectedTemplateId = "existing",
                        editorName = "续期提醒副本",
                        editorBody = "正文",
                    ),
                    availableVariables = emptyList(),
                    onSelect = {},
                    onStartNew = {},
                    onNameChanged = {},
                    onBodyChanged = {},
                    onOverwrite = { overwriteCount++ },
                    onSaveAs = { savedAsName = it },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("覆盖保存").performClick()
        composeRule.onNodeWithText("另存为").performClick()

        assertEquals(1, overwriteCount)
        assertEquals("续期提醒副本", savedAsName)
    }
}
