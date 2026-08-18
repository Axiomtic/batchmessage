package com.local.bulksms.model

data class MessageDraft(
    val rowId: Long,
    val phoneNumber: String,
    val backupPhoneNumber: String = "",
    val generatedBody: String,
    val currentBody: String,
    val syncWithTable: Boolean = true,
    val manuallyEdited: Boolean = false,
    val columnNames: List<String>,
    val phoneColumnIndex: Int?,
    val backupPhoneColumnIndex: Int? = null,
) {
    init {
        require(columnNames.isNotEmpty()) { "草稿必须包含动态列上下文" }
        require(columnNames.distinct().size == columnNames.size) { "草稿动态列名必须唯一" }
        require(phoneColumnIndex == null || phoneColumnIndex in columnNames.indices) {
            "手机号列索引超出动态列范围"
        }
        require(backupPhoneColumnIndex == null || backupPhoneColumnIndex in columnNames.indices) {
            "备用手机号列索引超出动态列范围"
        }
    }
}
