package com.local.bulksms.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.bulksms.data.TemplateDao
import com.local.bulksms.data.TemplateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TemplateUiState(
    val templates: List<TemplateEntity> = emptyList(),
    val selectedTemplateId: String? = null,
    val editorName: String = "",
    val editorBody: String = "",
    val validationError: String? = null,
)

class TemplateViewModel(
    templates: Flow<List<TemplateEntity>>,
    private val saveTemplate: suspend (TemplateEntity) -> Unit,
    private val deleteTemplate: suspend (String) -> Unit,
    scope: CoroutineScope? = null,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(TemplateUiState())
    val state: StateFlow<TemplateUiState> = mutableState.asStateFlow()
    private val workScope = scope ?: viewModelScope

    init {
        workScope.launch {
            templates.collect { values ->
                mutableState.update { it.copy(templates = values) }
            }
        }
    }

    fun selectTemplate(id: String) {
        val template = mutableState.value.templates.firstOrNull { it.id == id }
        mutableState.update {
            it.copy(
                selectedTemplateId = id,
                editorName = template?.name.orEmpty(),
                editorBody = template?.body.orEmpty(),
            )
        }
    }

    fun startNew() {
        mutableState.update {
            it.copy(
                selectedTemplateId = null,
                editorName = "",
                editorBody = "",
                validationError = null,
            )
        }
    }

    fun setEditorName(value: String) {
        mutableState.update { it.copy(editorName = value, validationError = null) }
    }

    fun setEditorBody(value: String) {
        mutableState.update { it.copy(editorBody = value, validationError = null) }
    }

    fun save() {
        val current = mutableState.value
        if (current.editorName.isBlank() || current.editorBody.isBlank()) {
            mutableState.update { it.copy(validationError = "模板名称和正文不能为空") }
            return
        }
        val existing = current.templates.firstOrNull { it.id == current.selectedTemplateId }
        val now = clock()
        val template = TemplateEntity(
            id = existing?.id ?: idFactory(),
            name = current.editorName.trim(),
            body = current.editorBody,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        mutableState.update { it.copy(selectedTemplateId = template.id, validationError = null) }
        workScope.launch { saveTemplate(template) }
    }

    fun overwrite() {
        val current = mutableState.value
        val existing = current.templates.firstOrNull { it.id == current.selectedTemplateId }
        if (existing == null) {
            mutableState.update { it.copy(validationError = "请先选择要覆盖的模板") }
            return
        }
        if (current.editorName.isBlank() || current.editorBody.isBlank()) {
            mutableState.update { it.copy(validationError = "模板名称和正文不能为空") }
            return
        }
        val updated = existing.copy(
            name = current.editorName.trim(),
            body = current.editorBody,
            updatedAt = clock(),
        )
        mutableState.update { it.copy(validationError = null) }
        workScope.launch { saveTemplate(updated) }
    }

    fun saveAs(name: String): String? {
        val current = mutableState.value
        if (name.isBlank() || current.editorBody.isBlank()) {
            mutableState.update { it.copy(validationError = "模板名称和正文不能为空") }
            return null
        }
        val now = clock()
        val created = TemplateEntity(
            id = idFactory(),
            name = name.trim(),
            body = current.editorBody,
            createdAt = now,
            updatedAt = now,
        )
        mutableState.update {
            it.copy(
                selectedTemplateId = created.id,
                editorName = created.name,
                validationError = null,
            )
        }
        workScope.launch { saveTemplate(created) }
        return created.id
    }

    fun delete(id: String) {
        mutableState.update { current ->
            if (current.selectedTemplateId == id) {
                current.copy(selectedTemplateId = null, editorName = "", editorBody = "")
            } else {
                current
            }
        }
        workScope.launch { deleteTemplate(id) }
    }

    companion object {
        fun fromDao(dao: TemplateDao): TemplateViewModel = TemplateViewModel(
            templates = dao.observeAll(),
            saveTemplate = dao::upsert,
            deleteTemplate = dao::deleteById,
        )
    }
}
