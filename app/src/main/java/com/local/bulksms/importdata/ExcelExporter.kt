package com.local.bulksms.importdata

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.local.bulksms.data.SendHistoryEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray

/**
 * Builds .xlsx bytes with Apache POI and shares them through the Android share sheet.
 * The history export keeps the same table shape as the data table, with the phone
 * columns reduced to the numbers that were actually sent.
 */
object ExcelExporter {

    /** Parsed view of a history entry, used by the history detail screen and export. */
    data class HistorySnapshot(
        val headerNames: List<String>,
        val rows: List<List<String>>,
        val phoneColumnIndex: Int?,
        val backupPhoneColumnIndex: Int?,
        val sentNumbers: Set<String>,
    ) {
        /** Rows reduced to the successfully sent numbers, dropping rows with none. */
        val exportRows: List<List<String>> by lazy {
            val phoneIndexes = listOfNotNull(phoneColumnIndex, backupPhoneColumnIndex)
            rows.mapNotNull { row ->
                val cells = row.toMutableList()
                var hasSentNumber = false
                for (index in phoneIndexes) {
                    val cell = cells.getOrNull(index).orEmpty()
                    val numbers = PhoneNumberChecker.extractPhoneNumbers(cell)
                    if (numbers.isEmpty()) continue
                    val sent = numbers.filter { it in sentNumbers }
                    if (sent.isNotEmpty()) hasSentNumber = true
                    cells[index] = sent.joinToString(";")
                }
                if (hasSentNumber) cells else null
            }
        }
    }

    fun snapshotOf(history: SendHistoryEntity): HistorySnapshot = HistorySnapshot(
        headerNames = decodeStrings(history.headerNamesJson),
        rows = decodeRows(history.rawRowsJson),
        phoneColumnIndex = history.phoneColumnIndex,
        backupPhoneColumnIndex = history.backupPhoneColumnIndex,
        sentNumbers = decodeStrings(history.sentNumbersJson).toSet(),
    )

    fun exportTable(headerNames: List<String>, rows: List<List<String>>): ByteArray =
        buildWorkbook(headerNames, rows)

    /** Exports a history entry: same columns as the snapshot table, phone cells kept
     *  to the successfully sent numbers; rows with no sent numbers are dropped. */
    fun exportHistory(history: SendHistoryEntity): ByteArray {
        val snapshot = snapshotOf(history)
        return buildWorkbook(snapshot.headerNames, snapshot.exportRows)
    }

    private fun buildWorkbook(headerNames: List<String>, rows: List<List<String>>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("数据")
        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
            alignment = HorizontalAlignment.CENTER
        }
        val headerRow = sheet.createRow(0)
        headerNames.forEachIndexed { index, name ->
            headerRow.createCell(index).apply {
                setCellValue(name)
                cellStyle = headerStyle
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            val sheetRow = sheet.createRow(rowIndex + 1)
            row.forEachIndexed { columnIndex, value ->
                sheetRow.createCell(columnIndex).setCellValue(value)
            }
        }
        return ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            output.toByteArray()
        }
    }

    fun shareXlsx(context: Context, fileName: String, bytes: ByteArray, title: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    fun formatHistoryTitle(history: SendHistoryEntity): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(java.util.Date(history.completedAt))
        return "发送历史 $date（成功 ${history.succeeded}/${history.total}）"
    }

    private fun decodeStrings(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { index -> array.getString(index) }
    }

    private fun decodeRows(json: String): List<List<String>> {
        val array = JSONArray(json)
        return List(array.length()) { rowIndex ->
            val row = array.getJSONArray(rowIndex)
            List(row.length()) { columnIndex -> row.getString(columnIndex) }
        }
    }
}
