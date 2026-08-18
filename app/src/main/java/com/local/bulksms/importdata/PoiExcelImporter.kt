package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable
import java.io.InputStream
import java.util.Locale
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * Reads the first worksheet of an .xls or .xlsx file using Apache POI.
 *
 * POI is the reference Excel implementation, so it handles the wide range of
 * real-world BIFF8 / OOXML variants produced by Microsoft Excel, WPS and others.
 * Cell values are rendered through [DataFormatter] so numbers, dates, booleans and
 * formula results all come out as display text.
 */
class PoiExcelImporter(
    private val maxRows: Int = HeaderDetector.MAX_DATA_ROWS + 1,
) : TableImporter {
    override fun import(input: InputStream): RawTable {
        val workbook = try {
            WorkbookFactory.create(input)
        } catch (exception: Exception) {
            throw IllegalArgumentException(
                "无法读取 Excel 文件：${exception.message ?: exception.javaClass.simpleName}",
                exception,
            )
        }
        workbook.use { wb ->
            if (wb.numberOfSheets == 0) {
                throw IllegalArgumentException("工作簿没有工作表")
            }
            val sheet = wb.getSheetAt(0)
            val formatter = DataFormatter(Locale.US)
            val lastRow = sheet.lastRowNum
            if (lastRow + 1 > maxRows) {
                throw ImportLimitExceeded(lastRow + 1)
            }

            val rawRows = mutableListOf<List<String>>()
            var width = 0
            for (rowIndex in 0..lastRow) {
                val row = sheet.getRow(rowIndex)
                val cells = if (row == null) {
                    emptyList()
                } else {
                    (0 until row.lastCellNum).map { columnIndex ->
                        val cell = row.getCell(columnIndex)
                        if (cell == null) "" else formatter.formatCellValue(cell)
                    }
                }
                width = maxOf(width, cells.size)
                rawRows += cells
            }

            val normalized = rawRows.map { row ->
                if (row.size >= width) row else row + List(width - row.size) { "" }
            }
            if (width == 0 || normalized.all { row -> row.all(String::isEmpty) }) {
                throw IllegalArgumentException("工作表为空")
            }
            return RawTable(rows = normalized)
        }
    }
}
