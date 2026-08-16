package com.local.bulksms.model

data class MessageDraft(
    val rowId: Long,
    val phoneNumber: String,
    val generatedBody: String,
    val currentBody: String,
    val syncWithTable: Boolean = true,
    val manuallyEdited: Boolean = false,
    val columnNames: List<String> = emptyList(),
    val phoneColumnIndex: Int? = null,
)
