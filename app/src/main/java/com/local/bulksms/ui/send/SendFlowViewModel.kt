package com.local.bulksms.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.importdata.HeaderDetector
import com.local.bulksms.importdata.PhoneColumnDetector
import com.local.bulksms.importdata.TabularTextParser
import com.local.bulksms.importdata.TableImporter
import com.local.bulksms.importdata.XlsxImporter
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.RawTable
import com.local.bulksms.template.DraftSynchronizer
import com.local.bulksms.template.TemplateRenderer
import com.local.bulksms.sms.SimOption
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SendFlowUiState(
    val rawTable: RawTable? = null,
    val table: ImportedTable? = null,
    val detectedHeader: Boolean = false,
    val selectedPhoneColumn: Int? = null,
    val importWarnings: List<String> = emptyList(),
    val blockingError: String? = null,
    val importId: String? = null,
    val selectedTemplateId: String? = null,
    val selectedTemplateBody: String? = null,
    val drafts: List<MessageDraft> = emptyList(),
    val missingTemplateVariables: Set<String> = emptySet(),
    val simOptions: List<SimOption> = emptyList(),
    val selectedSubscriptionId: Int? = null,
)

class SendFlowViewModel(
    private val xlsxImporter: TableImporter = XlsxImporter(),
    private val repository: BulkSmsRepository? = null,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(SendFlowUiState())
    val state: StateFlow<SendFlowUiState> = mutableState.asStateFlow()

    fun importClipboard(text: String) {
        importRaw { TabularTextParser.parse(text) }
    }

    fun importXlsx(input: InputStream) {
        importRaw { xlsxImporter.import(input) }
    }

    fun setFirstRowIsHeader(firstRowIsHeader: Boolean) {
        val raw = mutableState.value.rawTable ?: return
        setMaterializedTable(raw, firstRowIsHeader)
    }

    fun selectPhoneColumn(columnIndex: Int) {
        val current = mutableState.value
        val table = current.table ?: return
        if (columnIndex !in table.columns.indices) return
        mutableState.value = current.copy(
            selectedPhoneColumn = columnIndex,
            table = table.copy(phoneColumnIndex = columnIndex),
        )
    }

    fun setSimOptions(options: List<SimOption>) {
        val current = mutableState.value
        val selected = current.selectedSubscriptionId?.takeIf { id ->
            options.any { it.subscriptionId == id }
        } ?: options.singleOrNull()?.subscriptionId
        mutableState.value = current.copy(simOptions = options, selectedSubscriptionId = selected)
    }

    fun selectSubscription(subscriptionId: Int) {
        val current = mutableState.value
        if (current.simOptions.none { it.subscriptionId == subscriptionId }) return
        mutableState.value = current.copy(selectedSubscriptionId = subscriptionId)
    }

    fun selectTemplate(templateId: String, body: String) {
        val current = mutableState.value
        val table = current.table ?: return
        val renderer = TemplateRenderer(table)
        val missing = renderer.validate(body)
        if (missing.isNotEmpty()) {
            mutableState.value = current.copy(
                selectedTemplateId = templateId,
                selectedTemplateBody = body,
                drafts = emptyList(),
                missingTemplateVariables = missing,
                blockingError = "模板包含不存在的变量：${missing.joinToString("、")}",
            )
            persistDrafts(emptyList())
            return
        }
        val drafts = table.rows.map { renderer.renderDraft(it, body) }
        mutableState.value = current.copy(
            selectedTemplateId = templateId,
            selectedTemplateBody = body,
            drafts = drafts,
            missingTemplateVariables = emptySet(),
            blockingError = null,
        )
        persistDrafts(drafts)
    }

    fun editDraft(rowId: Long, body: String) {
        val current = mutableState.value
        val drafts = current.drafts.map { draft ->
            if (draft.rowId == rowId) DraftSynchronizer.editBody(draft, body) else draft
        }
        mutableState.value = current.copy(drafts = drafts)
        persistDrafts(drafts)
    }

    fun setDraftSynced(rowId: Long, synced: Boolean) {
        val current = mutableState.value
        val table = current.table ?: return
        val template = current.selectedTemplateBody ?: return
        val row = table.rows.firstOrNull { it.id == rowId } ?: return
        val drafts = current.drafts.map { draft ->
            if (draft.rowId == rowId) DraftSynchronizer.setSynced(draft, synced, row, template) else draft
        }
        mutableState.value = current.copy(drafts = drafts)
        persistDrafts(drafts)
    }

    fun editCell(rowId: Long, columnIndex: Int, value: String) {
        val current = mutableState.value
        val table = current.table ?: return
        val raw = current.rawTable ?: return
        val rawRowIndex = rowId.toInt() + if (table.firstRowIsHeader) 1 else 0
        val rawRow = raw.rows.getOrNull(rawRowIndex) ?: return
        if (columnIndex !in rawRow.indices) return

        val updatedRows = raw.rows.toMutableList().also { rows ->
            rows[rawRowIndex] = rawRow.toMutableList().also { cells ->
                cells[columnIndex] = value
            }
        }
        val updatedRaw = raw.copy(rows = updatedRows)
        val updatedTable = HeaderDetector.materialize(updatedRaw, table.firstRowIsHeader).copy(
            phoneColumnIndex = current.selectedPhoneColumn,
        )
        val refreshedDrafts = refreshDrafts(current, updatedTable)
        mutableState.value = current.copy(
            rawTable = updatedRaw,
            table = updatedTable,
            drafts = refreshedDrafts,
        )
        persistDrafts(refreshedDrafts)
    }

    private fun importRaw(importer: () -> RawTable) {
        runCatching(importer).fold(
            onSuccess = { raw ->
                val detectedHeader = HeaderDetector.detect(raw)
                val table = HeaderDetector.materialize(raw, detectedHeader)
                val phoneColumn = PhoneColumnDetector.recommend(table)
                mutableState.value = SendFlowUiState(
                    rawTable = raw,
                    table = table.copy(phoneColumnIndex = phoneColumn),
                    detectedHeader = detectedHeader,
                    selectedPhoneColumn = phoneColumn,
                    importWarnings = raw.warnings,
                    importId = idFactory(),
                )
            },
            onFailure = { error ->
                mutableState.value = SendFlowUiState(
                    blockingError = error.message ?: "无法导入数据",
                )
            },
        )
    }

    private fun setMaterializedTable(raw: RawTable, firstRowIsHeader: Boolean) {
        runCatching { HeaderDetector.materialize(raw, firstRowIsHeader) }.fold(
            onSuccess = { materialized ->
                val phoneColumn = PhoneColumnDetector.recommend(materialized)
                mutableState.value = mutableState.value.copy(
                    table = materialized.copy(phoneColumnIndex = phoneColumn),
                    selectedPhoneColumn = phoneColumn,
                    blockingError = null,
                )
            },
            onFailure = { error ->
                mutableState.value = mutableState.value.copy(
                    table = null,
                    selectedPhoneColumn = null,
                    blockingError = error.message ?: "无法处理数据",
                )
            },
        )
    }

    private fun refreshDrafts(current: SendFlowUiState, table: ImportedTable): List<MessageDraft> {
        val template = current.selectedTemplateBody ?: return current.drafts
        return current.drafts.mapNotNull { draft ->
            val row = table.rows.firstOrNull { it.id == draft.rowId } ?: return@mapNotNull null
            val currentPhone = table.phoneColumnIndex?.let { row.cells.getOrNull(it) }.orEmpty()
            DraftSynchronizer.regenerate(
                draft.copy(phoneNumber = currentPhone),
                row,
                template,
            )
        }
    }

    private fun persistDrafts(drafts: List<MessageDraft>) {
        val targetRepository = repository ?: return
        val importId = mutableState.value.importId ?: return
        viewModelScope.launch { targetRepository.saveDrafts(importId, drafts) }
    }
}
