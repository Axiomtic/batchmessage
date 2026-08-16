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
    fun launchStartsOnDataAndBottomNavigationOpensEveryPage() {
        composeRule.onNodeWithText("导入数据").assertExists()

        composeRule.onNodeWithText("短信").performClick()
        composeRule.onNodeWithText("短信预览").assertExists()

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("忽略首行").assertExists()
    }
}
