package com.local.bulksms.ui.send

import androidx.lifecycle.ViewModel
import com.local.bulksms.importdata.HeaderDetector
import com.local.bulksms.importdata.PhoneColumnDetector
import com.local.bulksms.importdata.TabularTextParser
import com.local.bulksms.importdata.TableImporter
import com.local.bulksms.importdata.XlsxImporter
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.RawTable
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SendFlowUiState(
    val rawTable: RawTable? = null,
    val table: ImportedTable? = null,
    val detectedHeader: Boolean = false,
    val selectedPhoneColumn: Int? = null,
    val importWarnings: List<String> = emptyList(),
    val blockingError: String? = null,
)

class SendFlowViewModel(
    private val xlsxImporter: TableImporter = XlsxImporter(),
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
        mutableState.value = current.copy(rawTable = updatedRaw, table = updatedTable)
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
}
