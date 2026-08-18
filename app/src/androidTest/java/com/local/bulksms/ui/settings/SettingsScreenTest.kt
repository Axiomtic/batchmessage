package com.local.bulksms.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.local.bulksms.sms.SimOption
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowViewModel
import com.local.bulksms.ui.send.SimDetectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsEditsHeaderSimAndSendIntervalWithoutPhoneColumnSection() {
        var ignoreFirstRow = true
        var selectedSubscription: Int? = null
        var sendIntervalMillis: Long? = null
        val state = SendFlowViewModel().state.value.copy(
            simOptions = listOf(SimOption(7, "SIM 1", 0)),
            simDetectionState = SimDetectionState.AVAILABLE,
            sendIntervalMillis = 1_000L,
        )

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onHeaderModeChanged = { ignoreFirstRow = it },
                        onSubscriptionSelected = { selectedSubscription = it },
                        onSendIntervalChanged = { sendIntervalMillis = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("ignore-first-row").performClick()
        assertFalse(ignoreFirstRow)
        composeRule.onNodeWithText("手机号列").assertDoesNotExist()
        composeRule.onNodeWithTag("send-interval-seconds").performTextReplacement("2.5")
        assertEquals(2_500L, sendIntervalMillis)
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("sim-7"))
        composeRule.onNodeWithTag("sim-7").performClick()
        assertEquals(7, selectedSubscription)
    }

    @Test
    fun missingPermissionShowsGrantActionInsteadOfEmptySimMessage() {
        var permissionRequested = false
        val state = SendFlowViewModel().state.value.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.PERMISSION_REQUIRED,
        )

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    callbacks = BulkSmsCallbacks(
                        onRequestSimPermission = { permissionRequested = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("需要电话权限才能读取 SIM").assertExists()
        composeRule.onNodeWithText("没有检测到活动 SIM").assertDoesNotExist()
        composeRule.onNodeWithTag("grant-sim-permission").performClick()
        assertEquals(true, permissionRequested)
    }

    @Test
    fun emptyStateDoesNotAskForPermissionAgain() {
        val state = SendFlowViewModel().state.value.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.EMPTY,
        )

        composeRule.setContent {
            MaterialTheme { SettingsScreen(state, BulkSmsCallbacks()) }
        }

        composeRule.onNodeWithText("没有检测到活动 SIM").assertExists()
        composeRule.onNodeWithTag("grant-sim-permission").assertDoesNotExist()
    }
}
