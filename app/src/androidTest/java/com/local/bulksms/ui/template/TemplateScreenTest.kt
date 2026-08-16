package com.local.bulksms.ui.template

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
                    onSave = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("variable-姓名").performClick()

        assertEquals("您好{姓名}", body)
    }
}
