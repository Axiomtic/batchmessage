package com.local.bulksms.template

import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRendererTest {
    private val table = ImportedTable(
        columns = listOf(
            DynamicColumn(0, "手机号"),
            DynamicColumn(1, "姓名"),
            DynamicColumn(2, "金额"),
        ),
        rows = listOf(
            DynamicRow(7L, listOf("13800138000", "张三", "100")),
        ),
        firstRowIsHeader = true,
        phoneColumnIndex = 0,
    )
    private val renderer = TemplateRenderer(table)
    private val row = table.rows.single()
    private val changedRow = DynamicRow(7L, listOf("13800138000", "张三", "200"))
    private val template = "{姓名}您好，金额{金额}"

    @Test
    fun validateReturnsDistinctMissingVariables() {
        assertEquals(setOf("日期"), renderer.validate("{姓名} {日期} {日期}", listOf("姓名")))
    }

    @Test
    fun renderReplacesEveryKnownVariable() {
        assertEquals("张三您好，金额100", renderer.render(template, mapOf("姓名" to "张三", "金额" to "100")))
    }

    @Test
    fun renderDraftUsesRowIdAndConfiguredPhoneColumn() {
        val draft = renderer.renderDraft(row, template)

        assertEquals(7L, draft.rowId)
        assertEquals("13800138000", draft.phoneNumber)
        assertEquals("张三您好，金额100", draft.generatedBody)
        assertEquals(draft.generatedBody, draft.currentBody)
        assertTrue(draft.syncWithTable)
        assertFalse(draft.manuallyEdited)
    }

    @Test
    fun manualEditDisablesSyncAndTableRefreshPreservesBody() {
        val original = renderer.renderDraft(row, template)
        val edited = DraftSynchronizer.editBody(original, "张三您好，已延期")

        assertFalse(edited.syncWithTable)
        assertTrue(edited.manuallyEdited)

        val refreshed = DraftSynchronizer.regenerate(edited, changedRow, template)

        assertEquals("张三您好，已延期", refreshed.currentBody)
        assertEquals("张三您好，金额100", refreshed.generatedBody)
    }

    @Test
    fun tableRefreshRegeneratesSyncedDraft() {
        val original = renderer.renderDraft(row, template)

        val refreshed = DraftSynchronizer.regenerate(original, changedRow, template)

        assertEquals("张三您好，金额200", refreshed.generatedBody)
        assertEquals("张三您好，金额200", refreshed.currentBody)
        assertTrue(refreshed.syncWithTable)
    }

    @Test
    fun reenablingSyncImmediatelyOverwritesManualBody() {
        val edited = DraftSynchronizer.editBody(
            renderer.renderDraft(row, template),
            "张三您好，已延期",
        )

        val synced = DraftSynchronizer.setSynced(edited, true, changedRow, template)

        assertEquals("张三您好，金额200", synced.currentBody)
        assertEquals("张三您好，金额200", synced.generatedBody)
        assertTrue(synced.syncWithTable)
        assertTrue(synced.manuallyEdited)
    }

    @Test
    fun refreshingResyncedDraftKeepsManualEditHistory() {
        val edited = DraftSynchronizer.editBody(
            renderer.renderDraft(row, template),
            "张三您好，已延期",
        )
        val synced = DraftSynchronizer.setSynced(edited, true, changedRow, template)

        val refreshed = DraftSynchronizer.regenerate(
            synced,
            DynamicRow(7L, listOf("13800138000", "张三", "300")),
            template,
        )

        assertTrue(refreshed.syncWithTable)
        assertTrue(refreshed.manuallyEdited)
        assertEquals("张三您好，金额300", refreshed.currentBody)
    }

    @Test
    fun rendererCanBuildDraftsFromColumnNames() {
        val namesOnlyRenderer = TemplateRenderer(listOf("姓名", "金额"), null)

        val draft = namesOnlyRenderer.renderDraft(
            DynamicRow(7L, listOf("张三", "100")),
            template,
        )

        assertEquals("张三您好，金额100", draft.currentBody)
        assertEquals("", draft.phoneNumber)
    }
}
