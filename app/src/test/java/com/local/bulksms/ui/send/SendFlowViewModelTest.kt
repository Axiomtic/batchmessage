package com.local.bulksms.ui.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SendFlowViewModelTest {
    @Test
    fun defaultStateStartsWithThreeColumnsFiveRowsAndGeneratedDrafts() {
        val state = SendFlowViewModel().state.value

        assertEquals(listOf("A", "B", "C"), state.table?.columns?.map { it.name })
        assertEquals(5, state.table?.rows?.size)
        assertEquals(1, state.selectedPhoneColumn)
        assertEquals(2, state.drafts.size)
        assertEquals(
            "您好，张三，您的服务将于2026-09-30到期，请及时办理续期。如已办理，请忽略本短信。",
            state.drafts.first().currentBody,
        )
    }

    @Test
    fun addingRowsAndColumnsKeepsTableDirectlyEditable() {
        val viewModel = SendFlowViewModel()

        viewModel.addRow()
        viewModel.addColumn()
        val state = viewModel.state.value

        assertEquals(6, state.table?.rows?.size)
        assertEquals(listOf("A", "B", "C", "D"), state.table?.columns?.map { it.name })
        viewModel.editCell(5L, 3, "现场输入")
        assertEquals("现场输入", viewModel.state.value.table?.rows?.last()?.cells?.last())
    }

    @Test
    fun importedRowsWaitForConfirmationWhenCurrentTableHasData() {
        val viewModel = SendFlowViewModel()

        viewModel.requestClipboardImport("名字\t电话\n王五\t13700137000")

        assertNotNull(viewModel.state.value.pendingImport)
        assertEquals("张三", viewModel.state.value.table?.rows?.first()?.cells?.first())
        viewModel.cancelPendingImport()
        assertNull(viewModel.state.value.pendingImport)
        assertEquals("张三", viewModel.state.value.table?.rows?.first()?.cells?.first())
    }

    @Test
    fun unsyncedDraftSurvivesConfirmedImport() {
        val viewModel = SendFlowViewModel()
        viewModel.editDraft(0L, "保留这条")

        viewModel.requestClipboardImport("名字\t电话\t服务到期日期\n王五\t13700137000\t2027-01-01")
        viewModel.confirmPendingImport()

        val draft = viewModel.state.value.drafts.first { it.rowId == 0L }
        assertEquals(false, draft.syncWithTable)
        assertEquals("保留这条", draft.currentBody)
        assertEquals("王五", viewModel.state.value.table?.rows?.first()?.cells?.first())
    }

    @Test
    fun liveTemplateEditAndBulkSyncRegenerateMappedDrafts() {
        val viewModel = SendFlowViewModel()
        viewModel.unsyncAllDrafts()
        val protectedBodies = viewModel.state.value.drafts.map { it.currentBody }

        viewModel.updateTemplateBody("{A}的新提醒")

        assertEquals(protectedBodies, viewModel.state.value.drafts.map { it.currentBody })
        viewModel.syncAllDrafts()
        assertEquals(listOf("张三的新提醒", "李四的新提醒"), viewModel.state.value.drafts.map { it.currentBody })
        assertNotEquals(protectedBodies, viewModel.state.value.drafts.map { it.currentBody })
    }

    @Test
    fun manualDraftEditIsProtectedUntilSyncIsReenabled() {
        val viewModel = SendFlowViewModel()
        viewModel.importClipboard("手机号\t姓名\t金额\n13800138000\t张三\t100")
        viewModel.selectTemplate("template-1", "{B}您好，金额{C}")

        viewModel.editDraft(0L, "张三您好，已延期")
        viewModel.editCell(0L, 2, "200")

        assertEquals(false, viewModel.state.value.drafts.single().syncWithTable)
        assertEquals("张三您好，已延期", viewModel.state.value.drafts.single().currentBody)

        viewModel.setDraftSynced(0L, true)

        assertEquals(true, viewModel.state.value.drafts.single().syncWithTable)
        assertEquals("张三您好，金额200", viewModel.state.value.drafts.single().currentBody)
    }

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
        assertEquals(listOf("A", "B"), state.table?.columns?.map { it.name })
        assertEquals(1, state.table?.rows?.size)
    }

    @Test
    fun togglingHeaderRebuildsColumnsAndPreservesRawRows() {
        val viewModel = SendFlowViewModel()

        viewModel.importClipboard("手机号\t姓名\n13800138000\t张三")
        viewModel.setFirstRowIsHeader(false)

        val state = viewModel.state.value
        assertNotNull(state.rawTable)
        assertEquals(listOf("A", "B"), state.table?.columns?.map { it.name })
        assertEquals("手机号", state.table?.rows?.first()?.cells?.first())
        assertEquals(2, state.rawTable?.rows?.size)
    }
}
