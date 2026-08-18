package com.local.bulksms.model

data class WorkspaceSnapshot(
    val importId: String,
    val rawRows: List<List<String>>,
    val detectedHeader: Boolean,
    val firstRowIsHeader: Boolean,
    val phoneColumnIndex: Int?,
    val backupPhoneColumnIndex: Int? = null,
    val selectedTemplateId: String?,
    val selectedTemplateName: String,
    val selectedTemplateBody: String,
    val selectedSubscriptionId: Int?,
) {
    companion object {
        const val DEFAULT_TEMPLATE_ID = "default-service-expiry"
        const val DEFAULT_TEMPLATE_NAME = "服务到期提醒"
        const val DEFAULT_TEMPLATE_BODY =
            "您好，{名字}，您的服务将于{服务到期日期}到期，请及时办理续期。如已办理，请忽略本短信。"

        fun sample(): WorkspaceSnapshot = WorkspaceSnapshot(
            importId = "current-import",
            rawRows = listOf(
                listOf("名字", "电话", "服务到期日期"),
                listOf("张三", "13800138000", "2026-09-30"),
                listOf("李四", "13900139000", "2026-10-15"),
                listOf("", "", ""),
                listOf("", "", ""),
                listOf("", "", ""),
            ),
            detectedHeader = true,
            firstRowIsHeader = true,
            phoneColumnIndex = 1,
            selectedTemplateId = DEFAULT_TEMPLATE_ID,
            selectedTemplateName = DEFAULT_TEMPLATE_NAME,
            selectedTemplateBody = DEFAULT_TEMPLATE_BODY,
            selectedSubscriptionId = null,
        )
    }
}
