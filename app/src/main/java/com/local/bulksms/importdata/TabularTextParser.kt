package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable

object TabularTextParser {
    fun parse(text: String): RawTable {
        val rows = text.lineSequence()
            .map { line ->
                line.removeSuffix("\r")
                    .replace(FOUR_SPACES, "\t")
                    .split('\t')
                    .map(String::trim)
            }
            .filterNot { row -> row.all(String::isBlank) }
            .toList()
        require(rows.isNotEmpty()) { "剪贴板中没有表格数据" }

        val width = rows.maxOf(List<String>::size)
        return RawTable(rows.map { row -> row + List(width - row.size) { "" } })
    }

    private const val FOUR_SPACES = "    "
}
