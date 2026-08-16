package com.local.bulksms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsFixedWorkbenchInsteadOfLegacyImportScreen() {
        composeRule.onNodeWithText("数据表格").assertExists()
        composeRule.onNodeWithText("现场模板").assertExists()
        composeRule.onNodeWithText("待发送短信", substring = true).assertExists()
        composeRule.onNodeWithText("确认并发送").assertExists()
        composeRule.onNodeWithText("导入数据").assertDoesNotExist()
    }

    @Test
    fun templateManagerOpensAndReturnsToWorkbench() {
        composeRule.onNodeWithText("模板管理").performClick()
        composeRule.onNodeWithText("短信模板").assertExists()

        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("数据表格").assertExists()
    }
}
