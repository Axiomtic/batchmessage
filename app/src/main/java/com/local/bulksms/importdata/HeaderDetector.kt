package com.local.bulksms.importdata

import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.RawTable
import com.local.bulksms.model.columnAddress

class ImportLimitExceeded(
    val actualRows: Int,
    val maxRows: Int = HeaderDetector.MAX_DATA_ROWS,
) : IllegalArgumentException("数据行数 $actualRows 超过上限 $maxRows")

object HeaderDetector {
    const val MAX_DATA_ROWS = 100

    fun detect(raw: RawTable): Boolean {
        if (raw.rows.size < 2) return false
        val firstRow = raw.rows.first()
        val sampleRows = raw.rows.drop(1).take(10)
        return firstRow.indices.any { columnIndex ->
            val heading = firstRow[columnIndex].trim()
            if (heading.isBlank() || valueKind(heading) != ValueKind.TEXT) return@any false

            val dataKinds = sampleRows.mapNotNull { row ->
                row.getOrNull(columnIndex)?.trim()?.takeIf(String::isNotBlank)?.let(::valueKind)
            }
            dataKinds.isNotEmpty() && dataKinds.count { it != ValueKind.TEXT } * 2 >= dataKinds.size
        }
    }

    fun materialize(raw: RawTable, firstRowIsHeader: Boolean): ImportedTable {
        val width = raw.rows.maxOfOrNull { it.size } ?: 0
        val dataRows = if (firstRowIsHeader) raw.rows.drop(1) else raw.rows
        if (dataRows.size > MAX_DATA_ROWS) {
            throw ImportLimitExceeded(dataRows.size)
        }

        val names = (0 until width).map(::columnAddress)
        val columns = names.mapIndexed { index, name -> DynamicColumn(id = index, name = name) }
        val rows = dataRows.mapIndexed { index, cells ->
            DynamicRow(id = index.toLong(), cells = cells.padTo(width))
        }

        return ImportedTable(
            columns = columns,
            rows = rows,
            firstRowIsHeader = firstRowIsHeader,
        )
    }

    private fun List<String>.padTo(width: Int): List<String> {
        if (size >= width) return toList()
        return this + List(width - size) { "" }
    }

    private fun valueKind(value: String): ValueKind = when {
        PhoneColumnDetector.isValid(value) -> ValueKind.PHONE
        value.toDoubleOrNull() != null -> ValueKind.NUMBER
        else -> ValueKind.TEXT
    }

    private enum class ValueKind { TEXT, NUMBER, PHONE }
}
