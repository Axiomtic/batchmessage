package com.local.bulksms.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsEditsSendIntervalOnly() {
        var sendIntervalMillis: Long? = null
        val state = SendFlowViewModel().state.value.copy(sendIntervalMillis = 1_000L)

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onSendIntervalChanged = { sendIntervalMillis = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("send-interval-seconds").performTextReplacement("2.5")
        assertEquals(2_500L, sendIntervalMillis)
    }
}
