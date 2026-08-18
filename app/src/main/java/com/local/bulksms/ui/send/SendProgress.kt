package com.local.bulksms.ui.send

import com.local.bulksms.model.SendStatus

data class SendProgressUiState(
    val total: Int,
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
    val running: Boolean,
) {
    companion object {
        fun from(statuses: List<SendStatus>): SendProgressUiState {
            val terminal = setOf(
                SendStatus.SUBMITTED,
                SendStatus.FAILED,
                SendStatus.UNCERTAIN,
                SendStatus.CANCELLED,
            )
            return SendProgressUiState(
                total = statuses.size,
                processed = statuses.count { it in terminal },
                succeeded = statuses.count { it == SendStatus.SUBMITTED },
                failed = statuses.count {
                    it == SendStatus.FAILED || it == SendStatus.UNCERTAIN || it == SendStatus.CANCELLED
                },
                running = statuses.any { it == SendStatus.PENDING || it == SendStatus.SUBMITTING },
            )
        }
    }
}
