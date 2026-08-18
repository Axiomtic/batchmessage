package com.local.bulksms.ui.send

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import com.local.bulksms.data.TemplateEntity
import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.sms.SimOption
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.template.TemplateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SmsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val template = TemplateEntity("default", "服务到期提醒", "您好，{A}")

    @Test
    fun templateActionsUseInlineAddDeleteAndDirtyGatedSave() {
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
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
    fun templateTitleAcceptsTextEditing() {
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
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

        composeRule.onNodeWithTag("template-selector").performTextReplacement("新的模板标题")
    }

    @Test
    fun templateTitleCanStayEmptyWhileEditing() {
        val state = SendFlowViewModel().state.value
        var editorName by mutableStateOf(template.name)
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
                    templateState = TemplateUiState(
                        templates = listOf(template),
                        selectedTemplateId = template.id,
                        editorName = editorName,
                        editorBody = template.body,
                        savedBody = template.body,
                    ),
                    callbacks = BulkSmsCallbacks(
                        onTemplateNameChanged = { editorName = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("template-selector").performTextClearance()
        composeRule.onNodeWithTag("template-selector").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        composeRule.onNodeWithTag("template-save").assertIsNotEnabled()
    }

    @Test
    fun saveEnablesOnlyForChangedBody() {
        val state = SendFlowViewModel().state.value
        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
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

    @Test
    fun simCanBeSelectedFromSendPage() {
        var selectedSubscription: Int? = null
        val state = SendFlowViewModel().state.value.copy(
            simOptions = listOf(SimOption(7, "SIM 1", 0), SimOption(9, "SIM 2", 1)),
            simDetectionState = SimDetectionState.AVAILABLE,
            selectedSubscriptionId = 7,
        )

        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
                    templateState = TemplateUiState(
                        templates = listOf(template),
                        selectedTemplateId = template.id,
                        editorName = template.name,
                        editorBody = template.body,
                        savedBody = template.body,
                    ),
                    callbacks = BulkSmsCallbacks(
                        onSubscriptionSelected = { selectedSubscription = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("sim-selector").performClick()
        composeRule.onNodeWithText("SIM 2").performClick()

        composeRule.runOnIdle { assertEquals(9, selectedSubscription) }
    }

    @Test
    fun missingSimPermissionShowsGrantActionOnSendPage() {
        var permissionRequested = false
        val state = SendFlowViewModel().state.value.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.PERMISSION_REQUIRED,
        )

        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
                    templateState = TemplateUiState(
                        templates = listOf(template),
                        selectedTemplateId = template.id,
                        editorName = template.name,
                        editorBody = template.body,
                        savedBody = template.body,
                    ),
                    callbacks = BulkSmsCallbacks(
                        onRequestSimPermission = { permissionRequested = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("需要电话权限才能读取 SIM").assertExists()
        composeRule.onNodeWithTag("grant-sim-permission").performClick()
        composeRule.runOnIdle { assertEquals(true, permissionRequested) }
    }

    @Test
    fun pageIsNamedSendAndVariableChipsAreNotShown() {
        val state = SendFlowViewModel().state.value

        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
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

        composeRule.onNodeWithText("发送").assertDoesNotExist()
        composeRule.onAllNodesWithText("{B}").assertCountEquals(0)
        composeRule.onAllNodesWithText("{C}").assertCountEquals(0)
    }

    @Test
    fun sendingProgressDisablesTemplateAndShowsOnlyOverallCounts() {
        val state = SendFlowViewModel().state.value.copy(
            sendProgress = SendProgressUiState(
                total = 80,
                processed = 12,
                succeeded = 10,
                failed = 2,
                running = true,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                SmsScreen(
                    state = state,
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

        composeRule.onNodeWithText("正在发送 12/80").assertExists()
        composeRule.onNodeWithTag("template-selector").assertIsNotEnabled()
        composeRule.onNodeWithText("138****8000").assertDoesNotExist()
    }

    @Test
    fun completedProgressShowsFinalSuccessAndFailureCounts() {
        val state = SendFlowViewModel().state.value.copy(
            sendProgress = SendProgressUiState(80, 80, 76, 4, false),
        )

        composeRule.setContent {
            MaterialTheme {
                SmsScreen(state, TemplateUiState(), BulkSmsCallbacks())
            }
        }

        composeRule.onNodeWithText("成功 76 条，失败 4 条").assertExists()
    }
}
