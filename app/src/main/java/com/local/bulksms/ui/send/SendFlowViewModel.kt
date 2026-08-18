package com.local.bulksms.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.data.TemplateEntity
import com.local.bulksms.importdata.ExcelImporter
import com.local.bulksms.importdata.HeaderDetector
import com.local.bulksms.importdata.PhoneColumnDetector
import com.local.bulksms.importdata.TabularTextParser
import com.local.bulksms.importdata.TableImporter
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.RawTable
import com.local.bulksms.model.WorkspaceSnapshot
import com.local.bulksms.sms.SimOption
import com.local.bulksms.sms.DEFAULT_SEND_INTERVAL_MILLIS
import com.local.bulksms.sms.MAX_SEND_INTERVAL_MILLIS
import com.local.bulksms.template.DraftSynchronizer
import com.local.bulksms.template.TemplateRenderer
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingImport(
    val rawTable: RawTable,
    val detectedHeader: Boolean,
)

enum class SimDetectionState {
    PERMISSION_REQUIRED,
    LOADING,
    AVAILABLE,
    EMPTY,
    ERROR,
}

data class SendFlowUiState(
    val rawTable: RawTable? = null,
    val table: ImportedTable? = null,
    val detectedHeader: Boolean = false,
    val selectedPhoneColumn: Int? = null,
    val selectedBackupPhoneColumn: Int? = null,
    val importWarnings: List<String> = emptyList(),
    val blockingError: String? = null,
    val importId: String? = null,
    val selectedTemplateId: String? = null,
    val selectedTemplateName: String = "",
    val selectedTemplateBody: String? = null,
    val templates: List<TemplateEntity> = emptyList(),
    val drafts: List<MessageDraft> = emptyList(),
    val selectedDraftRowIds: Set<Long> = emptySet(),
    val missingTemplateVariables: Set<String> = emptySet(),
    val simOptions: List<SimOption> = emptyList(),
    val simDetectionState: SimDetectionState = SimDetectionState.PERMISSION_REQUIRED,
    val simDetectionError: String? = null,
    val selectedSubscriptionId: Int? = null,
    val pendingImport: PendingImport? = null,
    val sendProgress: SendProgressUiState? = null,
    val sendIntervalMillis: Long = DEFAULT_SEND_INTERVAL_MILLIS,
    val workspaceReady: Boolean = true,
)

class SendFlowViewModel(
    private val excelImporter: TableImporter = ExcelImporter,
    private val repository: BulkSmsRepository? = null,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    initialWorkspace: WorkspaceSnapshot = WorkspaceSnapshot.sample(),
) : ViewModel() {
    private val persistenceChannel = Channel<SendFlowUiState>(Channel.CONFLATED)
    private var sendProgressJob: Job? = null
    private val mutableState = MutableStateFlow(
        stateFromWorkspace(initialWorkspace).copy(workspaceReady = repository == null),
    )
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
                val restored = stateFromWorkspace(workspace).copy(
                    sendIntervalMillis = mutableState.value.sendIntervalMillis,
                    workspaceReady = true,
                )
                mutableState.value = restored.replaceDrafts(restoredDrafts.ifEmpty { restored.drafts })
                if (restoredDrafts.isEmpty()) schedulePersistence(mutableState.value)
            }
            viewModelScope.launch {
                targetRepository.templateDao.observeAll().collect { templates ->
                    mutableState.value = mutableState.value.copy(templates = templates)
                }
            }
        }
    }

    fun importClipboard(text: String) =
        importRaw(importer = { TabularTextParser.parse(text) }, requireConfirmation = false)

    fun requestClipboardImport(text: String) =
        importRaw(importer = { TabularTextParser.parse(text) }, requireConfirmation = true)

    fun importXlsx(input: InputStream) =
        importRaw(importer = { excelImporter.import(input) }, requireConfirmation = false)

    fun requestXlsxImport(input: InputStream) =
        importRaw(importer = { excelImporter.import(input) }, requireConfirmation = true)

    fun importExcel(input: InputStream) = importXlsx(input)

    fun requestExcelImport(input: InputStream) = requestXlsxImport(input)

    fun reportImportError(message: String) = updateState {
        it.copy(pendingImport = null, blockingError = message)
    }

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
        val backup = current.selectedBackupPhoneColumn?.takeIf { it != columnIndex }
        val updatedTable = table.copy(
            phoneColumnIndex = columnIndex,
            backupPhoneColumnIndex = backup,
        )
        updateState {
            current.copy(
                selectedPhoneColumn = columnIndex,
                selectedBackupPhoneColumn = backup,
                table = updatedTable,
            ).replaceDrafts(refreshDrafts(current, updatedTable))
        }
    }

    /** The backup phone column cannot be the same as the primary one. */
    fun selectBackupPhoneColumn(columnIndex: Int) {
        val current = mutableState.value
        val table = current.table ?: return
        if (columnIndex !in table.columns.indices) return
        if (columnIndex == current.selectedPhoneColumn) return
        val updatedTable = table.copy(backupPhoneColumnIndex = columnIndex)
        updateState {
            current.copy(
                selectedBackupPhoneColumn = columnIndex,
                table = updatedTable,
            ).replaceDrafts(refreshDrafts(current, updatedTable))
        }
    }

    /**
     * Column-header tap cycling: tapping an unassigned column makes it the primary
     * phone column (or the backup when a primary already exists); tapping an
     * assigned column clears that assignment.
     */
    fun onColumnHeaderClicked(columnIndex: Int) {
        val current = mutableState.value
        val table = current.table ?: return
        if (columnIndex !in table.columns.indices) return
        when (columnIndex) {
            current.selectedPhoneColumn -> clearPhoneColumn()
            current.selectedBackupPhoneColumn -> clearBackupPhoneColumn()
            else -> {
                if (current.selectedPhoneColumn == null) {
                    selectPhoneColumn(columnIndex)
                } else if (current.selectedBackupPhoneColumn == null) {
                    selectBackupPhoneColumn(columnIndex)
                } else {
                    // Both roles are occupied: the tapped column becomes the new
                    // primary and the previous primary is released.
                    selectPhoneColumn(columnIndex)
                }
            }
        }
    }

    private fun clearPhoneColumn() {
        val current = mutableState.value
        val table = current.table ?: return
        val updatedTable = table.copy(phoneColumnIndex = null)
        updateState {
            current.copy(
                selectedPhoneColumn = null,
                table = updatedTable,
                blockingError = null,
            ).replaceDrafts(refreshDrafts(current, updatedTable))
        }
    }

    private fun clearBackupPhoneColumn() {
        val current = mutableState.value
        val table = current.table ?: return
        val updatedTable = table.copy(backupPhoneColumnIndex = null)
        updateState {
            current.copy(
                selectedBackupPhoneColumn = null,
                table = updatedTable,
                blockingError = null,
            ).replaceDrafts(refreshDrafts(current, updatedTable))
        }
    }

    fun toggleDraftSelection(rowId: Long, selected: Boolean) {
        val current = mutableState.value
        if (current.drafts.none { it.rowId == rowId }) return
        updateState {
            current.copy(
                selectedDraftRowIds = if (selected) {
                    current.selectedDraftRowIds + rowId
                } else {
                    current.selectedDraftRowIds - rowId
                },
            )
        }
    }

    fun selectAllDrafts(selected: Boolean) = updateState { current ->
        current.copy(
            selectedDraftRowIds = if (selected) {
                current.drafts.mapTo(mutableSetOf()) { it.rowId }
            } else {
                emptySet()
            },
        )
    }

    fun setSimOptions(options: List<SimOption>) {
        val current = mutableState.value
        val selected = current.selectedSubscriptionId?.takeIf { id ->
            options.any { it.subscriptionId == id }
        } ?: options.singleOrNull()?.subscriptionId
        updateState {
            current.copy(
                simOptions = options,
                selectedSubscriptionId = selected,
                simDetectionState = if (options.isEmpty()) SimDetectionState.EMPTY else SimDetectionState.AVAILABLE,
                simDetectionError = null,
            )
        }
    }

    fun setSimPermissionRequired() = updateState {
        it.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.PERMISSION_REQUIRED,
            simDetectionError = null,
        )
    }

    fun setSimLoading() = updateState {
        it.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.LOADING,
            simDetectionError = null,
        )
    }

    fun setSimDetectionError(message: String) = updateState {
        it.copy(
            simOptions = emptyList(),
            simDetectionState = SimDetectionState.ERROR,
            simDetectionError = message,
        )
    }

    fun selectSubscription(subscriptionId: Int) {
        val current = mutableState.value
        if (current.simOptions.none { it.subscriptionId == subscriptionId }) return
        updateState { current.copy(selectedSubscriptionId = subscriptionId) }
    }

    fun setSendInterval(intervalMillis: Long) = updateState {
        it.copy(sendIntervalMillis = intervalMillis.coerceIn(0L, MAX_SEND_INTERVAL_MILLIS))
    }

    suspend fun createSelectedSendTask(): String? {
        val current = mutableState.value
        val selectedDrafts = current.drafts.filter { it.rowId in current.selectedDraftRowIds }
        if (selectedDrafts.isEmpty()) {
            updateState { current.copy(blockingError = "请至少选择一条短信") }
            return null
        }
        if (current.selectedPhoneColumn == null && current.selectedBackupPhoneColumn == null) {
            updateState { current.copy(blockingError = "请先在数据表中选择电话号码列") }
            return null
        }
        val subscriptionId = current.selectedSubscriptionId
        if (subscriptionId == null || current.simOptions.none { it.subscriptionId == subscriptionId }) {
            updateState { current.copy(blockingError = "请先选择可用的发送 SIM") }
            return null
        }
        val targetRepository = repository
        val importId = current.importId
        if (targetRepository == null || importId == null) {
            updateState { current.copy(blockingError = "发送存储尚未就绪") }
            return null
        }
        targetRepository.saveDrafts(importId, current.drafts)
        val taskId = try {
            targetRepository.freezeQueue(
                importId = importId,
                simSubscriptionId = subscriptionId,
                selectedRowIds = current.selectedDraftRowIds,
            )
        } catch (error: IllegalArgumentException) {
            updateState { current.copy(blockingError = error.message ?: "无法创建发送任务") }
            return null
        }
        val itemCount = targetRepository.sendDao.itemsOnce(taskId).size
        mutableState.value = current.copy(
            blockingError = null,
            sendProgress = SendProgressUiState(
                total = itemCount,
                processed = 0,
                succeeded = 0,
                failed = 0,
                running = true,
            ),
        )
        return taskId
    }

    fun observeSendTask(taskId: String) {
        val targetRepository = repository ?: return
        sendProgressJob?.cancel()
        sendProgressJob = viewModelScope.launch {
            targetRepository.sendDao.items(taskId).collect { items ->
                mutableState.value = mutableState.value.copy(
                    sendProgress = SendProgressUiState.from(items.map { it.status }),
                )
            }
        }
    }

    fun setSendPermissionDenied() = updateState {
        it.copy(blockingError = "需要短信权限才能发送")
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
                missingTemplateVariables = missing,
                blockingError = missing.takeIf { it.isNotEmpty() }
                    ?.let { "模板包含不存在的变量：${it.joinToString("、")}" },
            ).replaceDrafts(
                if (missing.isEmpty()) refreshDrafts(withTemplate, table) else current.drafts,
            )
        }
    }

    fun selectTemplate(templateId: String) {
        val template = mutableState.value.templates.firstOrNull { it.id == templateId } ?: return
        selectTemplate(template.id, template.body, template.name)
    }

    fun overwriteSelectedTemplate() {
        val current = mutableState.value
        val body = current.selectedTemplateBody.orEmpty()
        val existing = current.templates.firstOrNull { it.id == current.selectedTemplateId }
        if (existing == null || body.isBlank()) {
            updateState { it.copy(blockingError = "请先选择模板并填写正文") }
            return
        }
        val updated = existing.copy(
            name = current.selectedTemplateName.ifBlank { existing.name },
            body = body,
            updatedAt = System.currentTimeMillis(),
        )
        updateState { it.copy(blockingError = null) }
        repository?.let { target -> viewModelScope.launch { target.templateDao.upsert(updated) } }
    }

    fun saveSelectedTemplateAs(name: String) {
        val current = mutableState.value
        val body = current.selectedTemplateBody.orEmpty()
        if (name.isBlank() || body.isBlank()) {
            updateState { it.copy(blockingError = "模板名称和正文不能为空") }
            return
        }
        val now = System.currentTimeMillis()
        val created = TemplateEntity(
            id = idFactory(),
            name = name.trim(),
            body = body,
            createdAt = now,
            updatedAt = now,
        )
        updateState {
            it.copy(
                selectedTemplateId = created.id,
                selectedTemplateName = created.name,
                blockingError = null,
            )
        }
        repository?.let { target -> viewModelScope.launch { target.templateDao.upsert(created) } }
    }

    fun updateTemplateBody(body: String) {
        val current = mutableState.value
        val table = current.table ?: return
        val missing = TemplateRenderer(table).validate(body)
        val withBody = current.copy(selectedTemplateBody = body)
        updateState {
            withBody.copy(
                missingTemplateVariables = missing,
                blockingError = missing.toTemplateError(),
            ).replaceDrafts(
                if (missing.isEmpty()) refreshDrafts(withBody, table) else current.drafts,
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

    fun deleteRow(rowId: Long) {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (table.rows.size <= 1) return
        val rawIndex = rowId.toInt() + if (table.firstRowIsHeader) 1 else 0
        if (rawIndex !in raw.rows.indices) return
        rematerializeCurrent(
            raw.copy(rows = raw.rows.filterIndexed { index, _ -> index != rawIndex }),
        )
    }

    fun deleteColumn(columnIndex: Int) {
        val current = mutableState.value
        val raw = current.rawTable ?: return
        val table = current.table ?: return
        if (table.columns.size <= 1 || columnIndex !in table.columns.indices) return
        fun adjust(index: Int?): Int? = when (val selected = index) {
            null -> null
            columnIndex -> null
            in (columnIndex + 1)..Int.MAX_VALUE -> selected - 1
            else -> selected
        }
        val adjustedPhoneColumn = adjust(current.selectedPhoneColumn)
        val adjustedBackupPhoneColumn = adjust(current.selectedBackupPhoneColumn)
        val updatedRaw = raw.copy(
            rows = raw.rows.map { row ->
                row.padTo(table.columns.size).filterIndexed { index, _ -> index != columnIndex }
            },
        )
        rematerializeCurrent(updatedRaw, adjustedPhoneColumn, adjustedBackupPhoneColumn)
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
                val missing = templateMissing(table)
                updateState {
                    current.copy(
                        rawTable = raw,
                        table = table,
                        detectedHeader = detectedHeader,
                        selectedPhoneColumn = phoneColumn,
                        selectedBackupPhoneColumn = null,
                        importWarnings = raw.warnings,
                        blockingError = missing.toTemplateError(),
                        missingTemplateVariables = missing,
                        importId = idFactory(),
                        pendingImport = null,
                    ).replaceDrafts(refreshDrafts(current, table))
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
                val missing = templateMissing(table)
                updateState {
                    current.copy(
                        table = table,
                        selectedPhoneColumn = phoneColumn,
                        selectedBackupPhoneColumn = null,
                        blockingError = missing.toTemplateError(),
                        missingTemplateVariables = missing,
                    ).replaceDrafts(refreshDrafts(current, table))
                }
            },
            onFailure = { error -> updateState { it.copy(blockingError = error.message ?: "无法处理数据") } },
        )
    }

    private fun rematerializeCurrent(
        updatedRaw: RawTable,
        selectedPhoneColumn: Int? = mutableState.value.selectedPhoneColumn,
        selectedBackupPhoneColumn: Int? = mutableState.value.selectedBackupPhoneColumn,
    ) {
        val current = mutableState.value
        val firstRowIsHeader = current.table?.firstRowIsHeader ?: current.detectedHeader
        runCatching { HeaderDetector.materialize(updatedRaw, firstRowIsHeader) }.fold(
            onSuccess = { materialized ->
                val selectedPhone = selectedPhoneColumn?.takeIf { it in materialized.columns.indices }
                val selectedBackup = selectedBackupPhoneColumn
                    ?.takeIf { it in materialized.columns.indices && it != selectedPhone }
                val table = materialized.copy(
                    phoneColumnIndex = selectedPhone,
                    backupPhoneColumnIndex = selectedBackup,
                )
                val missing = templateMissing(table)
                updateState {
                    current.copy(
                        rawTable = updatedRaw,
                        table = table,
                        selectedPhoneColumn = selectedPhone,
                        selectedBackupPhoneColumn = selectedBackup,
                        blockingError = missing.toTemplateError(),
                        missingTemplateVariables = missing,
                    ).replaceDrafts(refreshDrafts(current, table))
                }
            },
            onFailure = { error -> updateState { it.copy(blockingError = error.message ?: "无法处理数据") } },
        )
    }

    private fun refreshDrafts(current: SendFlowUiState, table: ImportedTable): List<MessageDraft> {
        val template = current.selectedTemplateBody ?: return current.drafts
        val renderer = TemplateRenderer(table)
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

    private fun templateMissing(table: ImportedTable): Set<String> {
        val template = mutableState.value.selectedTemplateBody ?: return emptySet()
        return TemplateRenderer(table).validate(template)
    }

    private fun SendFlowUiState.replaceDrafts(newDrafts: List<MessageDraft>): SendFlowUiState {
        val oldIds = drafts.mapTo(mutableSetOf()) { it.rowId }
        val newIds = newDrafts.mapTo(mutableSetOf()) { it.rowId }
        return copy(
            drafts = newDrafts,
            selectedDraftRowIds = reconcileDraftSelection(oldIds, selectedDraftRowIds, newIds),
        )
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
            backupPhoneColumnIndex = selectedBackupPhoneColumn,
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
            val backupColumn = workspace.backupPhoneColumnIndex
                ?.takeIf { it in materialized.columns.indices && it != phoneColumn }
            val table = materialized.copy(
                phoneColumnIndex = phoneColumn,
                backupPhoneColumnIndex = backupColumn,
            )
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
                selectedBackupPhoneColumn = backupColumn,
                importId = workspace.importId,
                selectedTemplateId = workspace.selectedTemplateId,
                selectedTemplateName = workspace.selectedTemplateName,
                selectedTemplateBody = workspace.selectedTemplateBody,
                drafts = drafts,
                selectedDraftRowIds = drafts.mapTo(mutableSetOf()) { it.rowId },
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
