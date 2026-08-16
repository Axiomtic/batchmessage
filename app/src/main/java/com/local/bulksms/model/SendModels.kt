package com.local.bulksms.model

/**
 * Durable state of a message in a frozen send queue.
 *
 * The names are persisted as strings by Room, so adding a state here is a
 * schema decision and must be accompanied by an explicit migration.
 */
enum class SendStatus {
    PENDING,
    SUBMITTING,
    SUBMITTED,
    FAILED,
    UNCERTAIN,
    CANCELLED,
}

/** Result written when a submission attempt receives a definitive outcome. */
data class SendAttemptResult(
    val status: SendStatus,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
) {
    init {
        require(status in setOf(SendStatus.SUBMITTED, SendStatus.FAILED, SendStatus.UNCERTAIN)) {
            "发送尝试结果必须是终态或不确定状态"
        }
    }
}
