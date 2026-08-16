package com.local.bulksms.ui.send

import com.local.bulksms.data.TemplateEntity
import com.local.bulksms.ui.template.TemplateViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateLifecycleTest {
    @Test
    fun saveOnlyEnablesAfterBodyChangesAndResetsAfterSave() = runTest {
        val defaultTemplate = TemplateEntity("default", "默认模板", "{A}旧正文")
        val templates = MutableStateFlow(listOf(defaultTemplate))
        val saved = mutableListOf<TemplateEntity>()
        val viewModel = TemplateViewModel(templates, { saved += it }, {}, backgroundScope)
        runCurrent()

        viewModel.selectTemplate(defaultTemplate.id)
        assertFalse(viewModel.state.value.isDirty)
        viewModel.setEditorBody("{A}新正文")
        assertTrue(viewModel.state.value.isDirty)
        assertEquals(defaultTemplate.id, viewModel.saveSelected()?.id)
        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun createSelectAndDeleteNeverLeaveZeroTemplates() = runTest {
        val defaultTemplate = TemplateEntity("default", "默认模板", "{A}正文")
        val templates = MutableStateFlow(listOf(defaultTemplate))
        val viewModel = TemplateViewModel(
            templates = templates,
            saveTemplate = { saved ->
                templates.value = templates.value.filterNot { it.id == saved.id } + saved
            },
            deleteTemplate = { id -> templates.value = templates.value.filterNot { it.id == id } },
            scope = backgroundScope,
            idFactory = { "created" },
        )
        runCurrent()
        viewModel.selectTemplate(defaultTemplate.id)

        val created = viewModel.create("新模板")
        runCurrent()
        assertEquals("新模板", created?.name)
        assertEquals("新模板", viewModel.state.value.editorName)

        assertEquals(defaultTemplate.id, viewModel.deleteSelected())
        runCurrent()
        assertEquals(1, viewModel.state.value.templates.size)
        assertNull(viewModel.deleteSelected())
    }
}
