package com.local.bulksms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchStartsOnDataAndBottomNavigationOpensEveryPage() {
        composeRule.onNodeWithText("导入数据").assertExists()

        composeRule.onNodeWithTag("nav-sms").performClick()
        composeRule.onNodeWithText("短信预览", substring = true).assertExists()

        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithText("发送间隔").assertExists()
    }
}
