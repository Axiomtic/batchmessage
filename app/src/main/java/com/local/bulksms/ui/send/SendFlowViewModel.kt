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
import com.local.bulksms.model.WorkspaceSnapshot
import com.local.bulksms.sms.SimOption
import com.local.bulksms.template.DraftSynchronizer
import com.local.bulksms.template.TemplateRenderer
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingImport(
    val rawTable: RawTable,
    val detectedHeader: Boolean,
)

data class SendFlowUiState(
    val rawTable: RawTable? = null,
    val table: ImportedTable? = null,
    val detectedHeader: Boolean = false,
    val selectedPhoneColumn: Int? = null,
    val importWarnings: List<String> = emptyList(),
    val blockingError: String? = null,
    val importId: String? = null,
    val selectedTemplateId: String? = null,
    val selectedTemplateName: String = "",
    val selectedTemplateBody: String? = null,
    val drafts: List<MessageDraft> = emptyList(),
    val missingTemplateVariables: Set<String> = emptySet(),
    val simOptions: List<SimOption> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val pendingImport: PendingImport? = null,
)

class SendFlowViewModel(
    private val xlsxImporter: TableImporter = XlsxImporter(),
    private val repository: BulkSmsRepository? = null,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    initialWorkspace: WorkspaceSnapshot = WorkspaceSnapshot.sample(),
) : ViewModel() {
    private val persistenceChannel = Channel<SendFlowUiState>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(stateFromWorkspace(initialWorkspace))
    val state: StateFlow<SendFlowUiState> = mutableState.asStateFlow()

    init {
        val targetRepository = repository
        if (targetRepository != null) {
            viewModelScope.launch {
                for (snapshot in persistenceChannel) {
                    val workspace = snapshot.toWorkspaceSnapshot() ?: continue
                    targetRepository.saveWorkspace(workspace)
                    targetRepository.saveDrafts(workspace.importId, snapshot.drafts)
                }
            }
            viewModelScope.launch {
                val workspace = targetRepository.loadOrCreateWorkspace()
                val restoredDrafts = targetRepository.loadDraftsOnce(workspace.importId)
                val restored = stateFromWorkspace(workspace)
                mutableState.value = restored.copy(
                    drafts = restoredDrafts.ifEmpty { restored.drafts },
                )
                if (restoredDrafts.isEmpty()) schedulePersistence(mutableState.value)
            }
        }
    }

    fun importClipboard(text: String) =
        importRaw(importer = { TabularTextParser.parse(text) }, requireConfirmation = false)

    fun requestClipboardImport(text: String) =
        importRaw(importer = { TabularTextParser.parse(text) }, requireConfirmation = true)

    fun importXlsx(input: InputStream) =
        importRaw(importer = { xlsxImporter.import(input) }, requireConfirmation = false)

    fun requestXlsxImport(input: InputStream) =
        importRaw(importer = { xlsxImporter.import(input) }, requireConfirmation = true)

    fun confirmPendingImport() {
        val pending = mutableState.value.pendingImport ?: return
        applyImportedRaw(pending.rawTable, pending.detectedHeader)
    }

    fun cancelPendingImport() = updateState { it.copy(pendingImport = null) }

    fun setFirstRowIsHeader(firstRowIsHeader: Boolean) {
        val raw = mutableState.value.rawTable ?: return
        setMaterializedTable(raw, firstRowIsHeader)
    }

    fun selectPhoneColumn(columnIndex: Int) {
        val current = mutableState.value
        val table = current.table ?: return
        if (columnIndex !in table.columns.indices) return
        val updatedTable = table.copy(phoneColumnIndex = columnIndex)
        updateState {
            current.copy(
                selectedPhoneColumn = columnIndex,
                table = updatedTable,
                drafts = refreshDrafts(current, updatedTable),
            )
        }
    }

    fun setSimOptions(options: List<SimOption>) {
        val current = mutableState.value
        val selected = current.selectedSubscriptionId?.takeIf { id ->
            options.any { it.subscriptionId == id }
        } ?: options.singleOrNull()?.subscriptionId
        updateState { current.copy(simOptions = options, selectedSubscriptionId = selected) }
    }

    fun selectSubscription(subscriptionId: Int) {
        val current = mutableState.value
        if (current.simOptions.none { it.subscriptionId == subscriptionId }) return
        updateState { current.copy(selectedSubscriptionId = subscriptionId) }
    }

    fun selectTemplate(templateId: String, body: String, name: String = templateId) {
        val current = mutableState.value
        val table = current.table ?: return
        val missing = TemplateRenderer(table).validate(body)
        val withTemplate = current.copy(
            selectedTemplateId = templateId,
            selectedTemplateName = name,
            selectedTemplateBody = body,
        )
        updateState {
            withTemplate.copy(
                drafts = if (missing.isEmpty()) refreshDrafts(withTemplate, table) else current.drafts,
                missingTemplateVariables = missing,
                blockingError = missing.takeIf { it.isNotEmpty() }
                    ?.let { "模板包含不存在的变量：${it.joinToString("、")}" },
            )
        }
    }

    fun updateTemplateBody(body: String) {
        val current = mutableState.value
        val table = current.table ?: return
        val missing = TemplateRenderer(table).validate(body)
        val withBody = current.copy(selectedTemplateBody = body)
        updateState {
            withBody.copy(
                drafts = if (missing.isEmpty()) refreshDrafts(withBody, table) else current.drafts,
                missingTemplateVariables = missing,
                blockingError = missing.toTemplateError(),
            )
        }
    }

    fun editDraft(rowId: Long, body: String) {
        val current = mutableState.value
        updateState {
            current.copy(drafts = current.drafts.map { draft ->
                if (draft.rowId == rowId) DraftSynchronizer.editBody(draft, body) else draft
            })
        }
    }

    fun setDraftSynced(rowId: Long, synced: Boolean) {
        val current = mutableState.value
        val table = current.table ?: return
        val template = current.selectedTemplateBody ?: return
        val row = table.rows.firstOrNull { it.id == rowId }
        if (synced && row == null) {
            updateState { current.copy(blockingError = "这条短信已没有对应的表格行，无法恢复同步") }
            return
        }
        updateState {
            current.copy(
                drafts = current.drafts.map { draft ->
                    when {
                        draft.rowId != rowId -> draft
                        !synced -> draft.copy(syncWithTable = false)
                        else -> DraftSynchronizer.setSynced(draft, true, requireNotNull(row), template)
                    }
                },
                blockingError = null,
            )
        }
    }

    fun unsyncAllDrafts() = updateState { current ->
        current.copy(drafts = current.drafts.map { it.copy(syncWithTable = false) })
    }

    fun syncAllDrafts() {
        val current = mutableState.value
        val table = current.table ?: return
        val template = current.selectedTemplateBody ?: return
        val rows = table.rows.associateBy { it.id }
        val detached = current.drafts.any { it.rowId !in rows }
        updateState {
            current.copy(
                drafts = current.drafts.map { draft ->
                    rows[draft.rowId]?.let { row ->
                        DraftSynchronizer.setSynced(draft, true, row, template)
                    } ?: draft.copy(syncWithTable = false)
                },
                blockingError = if (detached) "没有对应表格行的独立短信保持不同步" else null,
            )
        }
    }

    fun editCell(rowId: Long, columnIndex: Int, value: String) {
        val current = mutableState.value
        val table = current.table ?: return
        val raw = current.rawTable ?: return
        val rawRowIndex = rowId.toInt() + if (table.firstRowIsHeader) 1 else 0
        val rawRow = raw.rows.getOrNull(rawRowIndex) ?: return
        if (columnIndex !in table.columns.indices) return
        val updatedRows = raw.rows.toMutableList().also { rows ->
            rows[rawRowIndex] = rawRow.padTo(table.columns.size).toMutableList().also { cells ->
                cells[columnIndex] = value
            }
        }
        rematerializeCurrent(raw.copy(rows = updatedRows))
    }

    fun editHeader(columnIndex: Int, value: String) {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (!table.firstRowIsHeader || columnIndex !in table.columns.indices) return
        val header = raw.rows.firstOrNull().orEmpty().padTo(table.columns.size).toMutableList()
        header[columnIndex] = value
        rematerializeCurrent(raw.copy(rows = listOf(header) + raw.rows.drop(1)))
    }

    fun addRow() {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (table.rows.size >= HeaderDetector.MAX_DATA_ROWS) {
            updateState { current.copy(blockingError = "数据行数不能超过 ${HeaderDetector.MAX_DATA_ROWS}") }
            return
        }
        rematerializeCurrent(raw.copy(rows = raw.rows + listOf(List(table.columns.size) { "" })))
    }

    fun addColumn() {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        val newName = (table.columns.size + 1).toString()
        val updatedRows = raw.rows.mapIndexed { index, row ->
            row.padTo(table.columns.size) + if (index == 0 && table.firstRowIsHeader) newName else ""
        }
        rematerializeCurrent(raw.copy(rows = updatedRows))
    }

    fun deleteLastRow() {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (table.rows.size > 1) rematerializeCurrent(raw.copy(rows = raw.rows.dropLast(1)))
    }

    fun deleteLastColumn() {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (table.columns.size > 1) rematerializeCurrent(raw.copy(rows = raw.rows.map { it.dropLast(1) }))
    }

    fun clearTable() {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        val header = if (table.firstRowIsHeader) listOf(raw.rows.first().padTo(table.columns.size)) else emptyList()
        rematerializeCurrent(raw.copy(rows = header + List(5) { List(table.columns.size) { "" } }))
    }

    private fun importRaw(importer: () -> RawTable, requireConfirmation: Boolean) {
        runCatching(importer).fold(
            onSuccess = { raw ->
                val detectedHeader = HeaderDetector.detect(raw)
                HeaderDetector.materialize(raw, detectedHeader)
                if (requireConfirmation && hasExistingData(mutableState.value.table)) {
                    updateState { it.copy(pendingImport = PendingImport(raw, detectedHeader), blockingError = null) }
                } else {
                    applyImportedRaw(raw, detectedHeader)
                }
            },
            onFailure = { error ->
                updateState { it.copy(pendingImport = null, blockingError = error.message ?: "无法导入数据") }
            },
        )
    }

    private fun applyImportedRaw(raw: RawTable, detectedHeader: Boolean) {
        runCatching { HeaderDetector.materialize(raw, detectedHeader) }.fold(
            onSuccess = { materialized ->
                val current = mutableState.value
                val phoneColumn = PhoneColumnDetector.recommend(materialized)
                val table = materialized.copy(phoneColumnIndex = phoneColumn)
                updateState {
                    current.copy(
                        rawTable = raw,
                        table = table,
                        detectedHeader = detectedHeader,
                        selectedPhoneColumn = phoneColumn,
                        importWarnings = raw.warnings,
                        blockingError = null,
                        importId = idFactory(),
                        drafts = refreshDrafts(current, table),
                        pendingImport = null,
                    )
                }
            },
            onFailure = { error -> updateState { it.copy(blockingError = error.message ?: "无法处理数据") } },
        )
    }

    private fun setMaterializedTable(raw: RawTable, firstRowIsHeader: Boolean) {
        runCatching { HeaderDetector.materialize(raw, firstRowIsHeader) }.fold(
            onSuccess = { materialized ->
                val current = mutableState.value
                val phoneColumn = PhoneColumnDetector.recommend(materialized)
                val table = materialized.copy(phoneColumnIndex = phoneColumn)
                updateState {
                    current.copy(
                        table = table,
                        selectedPhoneColumn = phoneColumn,
                        drafts = refreshDrafts(current, table),
                        blockingError = null,
                    )
                }
            },
            onFailure = { error -> updateState { it.copy(blockingError = error.message ?: "无法处理数据") } },
        )
    }

    private fun rematerializeCurrent(updatedRaw: RawTable) {
        val current = mutableState.value
        val firstRowIsHeader = current.table?.firstRowIsHeader ?: current.detectedHeader
        runCatching { HeaderDetector.materialize(updatedRaw, firstRowIsHeader) }.fold(
            onSuccess = { materialized ->
                val selectedPhone = current.selectedPhoneColumn?.takeIf { it in materialized.columns.indices }
                val table = materialized.copy(phoneColumnIndex = selectedPhone)
                updateState {
                    current.copy(
                        rawTable = updatedRaw,
                        table = table,
                        selectedPhoneColumn = selectedPhone,
                        drafts = refreshDrafts(current, table),
                        blockingError = null,
                    )
                }
            },
            onFailure = { error -> updateState { it.copy(blockingError = error.message ?: "无法处理数据") } },
        )
    }

    private fun refreshDrafts(current: SendFlowUiState, table: ImportedTable): List<MessageDraft> {
        val template = current.selectedTemplateBody ?: return current.drafts
        val renderer = TemplateRenderer(table)
        if (renderer.validate(template).isNotEmpty()) return current.drafts
        val rowsWithData = table.rows.filter { row -> row.cells.any { it.isNotBlank() } }
        val rowIds = rowsWithData.mapTo(mutableSetOf()) { it.id }
        val existing = current.drafts.associateBy { it.rowId }
        val refreshed = rowsWithData.map { row ->
            existing[row.id]?.let { draft -> DraftSynchronizer.regenerate(draft, row, template, renderer) }
                ?: renderer.renderDraft(row, template)
        }
        val detached = current.drafts.filter { !it.syncWithTable && it.rowId !in rowIds }
        return refreshed + detached
    }

    private fun updateState(transform: (SendFlowUiState) -> SendFlowUiState) {
        val updated = transform(mutableState.value)
        mutableState.value = updated
        schedulePersistence(updated)
    }

    private fun schedulePersistence(snapshot: SendFlowUiState) {
        if (repository != null) persistenceChannel.trySend(snapshot)
    }

    private fun SendFlowUiState.toWorkspaceSnapshot(): WorkspaceSnapshot? {
        val raw = rawTable ?: return null
        return WorkspaceSnapshot(
            importId = importId ?: return null,
            rawRows = raw.rows,
            detectedHeader = detectedHeader,
            firstRowIsHeader = table?.firstRowIsHeader ?: detectedHeader,
            phoneColumnIndex = selectedPhoneColumn,
            selectedTemplateId = selectedTemplateId,
            selectedTemplateName = selectedTemplateName,
            selectedTemplateBody = selectedTemplateBody.orEmpty(),
            selectedSubscriptionId = selectedSubscriptionId,
        )
    }

    private fun List<String>.padTo(width: Int): List<String> =
        if (size >= width) take(width) else this + List(width - size) { "" }

    private fun Set<String>.toTemplateError(): String? =
        takeIf { it.isNotEmpty() }?.let { "模板包含不存在的变量：${it.joinToString("、")}" }

    companion object {
        private fun stateFromWorkspace(workspace: WorkspaceSnapshot): SendFlowUiState {
            val raw = RawTable(workspace.rawRows)
            val materialized = HeaderDetector.materialize(raw, workspace.firstRowIsHeader)
            val phoneColumn = workspace.phoneColumnIndex?.takeIf { it in materialized.columns.indices }
            val table = materialized.copy(phoneColumnIndex = phoneColumn)
            val renderer = TemplateRenderer(table)
            val missing = renderer.validate(workspace.selectedTemplateBody)
            val drafts = if (missing.isEmpty()) {
                table.rows.filter { row -> row.cells.any { it.isNotBlank() } }
                    .map { row -> renderer.renderDraft(row, workspace.selectedTemplateBody) }
            } else emptyList()
            return SendFlowUiState(
                rawTable = raw,
                table = table,
                detectedHeader = workspace.detectedHeader,
                selectedPhoneColumn = phoneColumn,
                importId = workspace.importId,
                selectedTemplateId = workspace.selectedTemplateId,
                selectedTemplateName = workspace.selectedTemplateName,
                selectedTemplateBody = workspace.selectedTemplateBody,
                drafts = drafts,
                missingTemplateVariables = missing,
                blockingError = missing.takeIf { it.isNotEmpty() }
                    ?.let { "模板包含不存在的变量：${it.joinToString("、")}" },
                selectedSubscriptionId = workspace.selectedSubscriptionId,
            )
        }

        private fun hasExistingData(table: ImportedTable?): Boolean =
            table?.rows?.any { row -> row.cells.any { it.isNotBlank() } } == true
    }
}
