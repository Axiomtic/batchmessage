package com.local.bulksms.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.local.bulksms.sms.SimOption
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsEditsSharedParsingAndRadioSelections() {
        var ignoreFirstRow = true
        var selectedPhoneColumn: Int? = null
        var selectedSubscription: Int? = null
        val state = SendFlowViewModel().state.value.copy(
            simOptions = listOf(SimOption(7, "SIM 1", 0)),
        )

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onHeaderModeChanged = { ignoreFirstRow = it },
                        onPhoneColumnSelected = { selectedPhoneColumn = it },
                        onSubscriptionSelected = { selectedSubscription = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("ignore-first-row").performClick()
        assertFalse(ignoreFirstRow)
        composeRule.onNodeWithTag("phone-column-C").performClick()
        assertEquals(2, selectedPhoneColumn)
        composeRule.onNodeWithTag("sim-7").performClick()
        assertEquals(7, selectedSubscription)
    }
}
