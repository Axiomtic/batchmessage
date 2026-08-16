package com.local.bulksms.importdata

import com.local.bulksms.model.ImportedTable

object PhoneColumnDetector {
    private val normalizedPhonePattern = Regex("\\+?[0-9]{7,15}")

    fun isValid(value: String): Boolean {
        return normalize(value).matches(normalizedPhonePattern)
    }

    fun recommend(table: ImportedTable): Int? {
        if (table.rows.isEmpty() || table.columns.isEmpty()) return null

        val validCounts = table.columns.indices.map { columnIndex ->
            table.rows.count { row ->
                row.cells.getOrNull(columnIndex)?.let(::isValid) == true
            }
        }
        val highest = validCounts.maxOrNull() ?: return null
        if (highest == 0) return null

        return validCounts
            .mapIndexedNotNull { index, count -> index.takeIf { count == highest } }
            .singleOrNull()
    }

    private fun normalize(value: String): String {
        return value.filterNot { character ->
            character.isWhitespace() || character == '-' || character == '(' || character == ')'
        }
    }
}
