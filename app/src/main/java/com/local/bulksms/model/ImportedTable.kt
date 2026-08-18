package com.local.bulksms.model

data class RawTable(
    val rows: List<List<String>>,
    val warnings: List<String> = emptyList(),
)

data class DynamicColumn(
    val id: Int,
    val name: String,
)

data class DynamicRow(
    val id: Long,
    val cells: List<String>,
)

data class ImportedTable(
    val columns: List<DynamicColumn>,
    val rows: List<DynamicRow>,
    val firstRowIsHeader: Boolean,
    val phoneColumnIndex: Int? = null,
    val backupPhoneColumnIndex: Int? = null,
)
