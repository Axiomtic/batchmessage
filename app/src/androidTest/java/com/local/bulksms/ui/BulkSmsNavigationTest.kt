package com.local.bulksms.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.local.bulksms.ui.send.SendFlowViewModel
import com.local.bulksms.ui.template.TemplateUiState
import org.junit.Rule
import org.junit.Test

class BulkSmsNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationUsesSemanticIconsForAllPages() {
        composeRule.setContent {
            MaterialTheme {
                BulkSmsApp(
                    sendState = SendFlowViewModel().state.value,
                    templateState = TemplateUiState(),
                    callbacks = BulkSmsCallbacks(),
                )
            }
        }

        composeRule.onNodeWithTag("nav-data").assertExists()
        composeRule.onNodeWithTag("nav-sms").assertExists()
        composeRule.onNodeWithTag("nav-settings").assertExists()
    }
}
