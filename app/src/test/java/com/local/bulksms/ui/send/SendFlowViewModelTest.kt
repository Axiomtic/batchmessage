package com.local.bulksms.ui.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SendFlowViewModelTest {
    @Test
    fun selectingPhoneColumnOverridesRecommendation() {
        val viewModel = SendFlowViewModel()
        viewModel.importClipboard("13800138000\t13900139000\n13700137000\t13600136000")

        viewModel.selectPhoneColumn(1)

        assertEquals(1, viewModel.state.value.selectedPhoneColumn)
        assertEquals(1, viewModel.state.value.table?.phoneColumnIndex)
    }

    @Test
    fun editingCellSurvivesHeaderRematerialization() {
        val viewModel = SendFlowViewModel()
        viewModel.importClipboard("手机号\t姓名\n13800138000\t张三")

        viewModel.editCell(rowId = 0L, columnIndex = 1, value = "张三丰")
        viewModel.setFirstRowIsHeader(false)

        val state = viewModel.state.value
        assertEquals("张三丰", state.rawTable?.rows?.get(1)?.get(1))
        assertEquals("张三丰", state.table?.rows?.get(1)?.cells?.get(1))
    }

    @Test
    fun importingClipboardDetectsTextHeaderAbovePhoneData() {
        val viewModel = SendFlowViewModel()

        viewModel.importClipboard("手机号\t姓名\n13800138000\t张三")

        val state = viewModel.state.value
        assertEquals(true, state.detectedHeader)
        assertEquals(true, state.table?.firstRowIsHeader)
        assertEquals(listOf("手机号", "姓名"), state.table?.columns?.map { it.name })
        assertEquals(1, state.table?.rows?.size)
    }

    @Test
    fun togglingHeaderRebuildsColumnsAndPreservesRawRows() {
        val viewModel = SendFlowViewModel()

        viewModel.importClipboard("手机号\t姓名\n13800138000\t张三")
        viewModel.setFirstRowIsHeader(false)

        val state = viewModel.state.value
        assertNotNull(state.rawTable)
        assertEquals(listOf("列1", "列2"), state.table?.columns?.map { it.name })
        assertEquals("手机号", state.table?.rows?.first()?.cells?.first())
        assertEquals(2, state.rawTable?.rows?.size)
    }
}
