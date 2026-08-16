package com.local.bulksms.ui.template

import com.local.bulksms.data.TemplateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateViewModelTest {
    @Test
    fun overwriteKeepsIdWhileSaveAsCreatesNewId() = runTest {
        val existing = TemplateEntity("existing", "到期提醒", "旧正文", createdAt = 10L, updatedAt = 10L)
        val templates = MutableStateFlow(listOf(existing))
        val saved = mutableListOf<TemplateEntity>()
        val viewModel = TemplateViewModel(
            templates = templates,
            saveTemplate = { saved += it },
            deleteTemplate = {},
            scope = backgroundScope,
            idFactory = { "generated-new-id" },
            clock = { 20L },
        )
        runCurrent()
        viewModel.selectTemplate(existing.id)
        viewModel.setEditorBody("修改正文")

        viewModel.overwrite()
        val savedAsId = viewModel.saveAs("新的到期提醒")
        runCurrent()

        assertEquals(listOf("existing", "generated-new-id"), saved.map { it.id })
        assertEquals(10L, saved.first().createdAt)
        assertEquals("新的到期提醒", saved.last().name)
        assertEquals("修改正文", saved.last().body)
        assertEquals("generated-new-id", viewModel.state.value.selectedTemplateId)
        assertEquals("generated-new-id", savedAsId)
    }

    @Test
    fun savingNewTemplatePersistsEditorValuesAndSelectsIt() = runTest {
        val templates = MutableStateFlow(emptyList<TemplateEntity>())
        val saved = mutableListOf<TemplateEntity>()
        val viewModel = TemplateViewModel(
            templates = templates,
            saveTemplate = { template -> saved += template },
            deleteTemplate = {},
            scope = backgroundScope,
            idFactory = { "template-new" },
            clock = { 1234L },
        )
        viewModel.startNew()
        viewModel.setEditorName("催款")
        viewModel.setEditorBody("{姓名}您好")

        viewModel.save()
        runCurrent()

        assertEquals("template-new", viewModel.state.value.selectedTemplateId)
        assertEquals("催款", saved.single().name)
        assertEquals("{姓名}您好", saved.single().body)
        assertEquals(1234L, saved.single().createdAt)
    }

    @Test
    fun deletingSelectedTemplateClearsSelection() = runTest {
        val template = TemplateEntity("template-1", "催款", "{姓名}您好")
        val templates = MutableStateFlow(listOf(template))
        val viewModel = TemplateViewModel(
            templates = templates,
            saveTemplate = { saved -> templates.value = listOf(saved) },
            deleteTemplate = { id -> templates.value = templates.value.filterNot { it.id == id } },
            scope = backgroundScope,
        )
        viewModel.selectTemplate(template.id)

        viewModel.delete(template.id)

        assertNull(viewModel.state.value.selectedTemplateId)
    }
}
