package com.local.bulksms.sms

import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.data.SendItemEntity
import com.local.bulksms.model.SendStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class SmsQueueProcessor(
    private val repository: BulkSmsRepository,
    private val gateway: SmsGateway,
    private val sendIntervalMillis: Long = 0L,
    private val waitBetweenMessages: suspend (Long) -> Unit = { delay(it) },
    private val onProgress: suspend (List<SendItemEntity>) -> Unit = {},
) {
    suspend fun process(taskId: String) {
        repository.recoverInterruptedAttempts(taskId)
        val task = repository.sendDao.taskOnce(taskId) ?: return
        onProgress(repository.sendDao.itemsOnce(taskId))
        while (true) {
            val item = repository.claimNext(taskId) ?: break
            val result = runCatching {
                withTimeout(SUBMISSION_TIMEOUT_MILLIS) {
                    gateway.submit(
                        SmsSubmission(
                            itemId = item.id,
                            subscriptionId = task.simSubscriptionId,
                            phone = item.phoneNumber,
                            body = item.body,
                        ),
                    )
                }
            }
            result.fold(
                onSuccess = { submission ->
                    repository.completeAttempt(
                        itemId = item.id,
                        status = if (submission.success) SendStatus.SUBMITTED else SendStatus.FAILED,
                        errorCode = submission.errorCode,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    repository.completeAttempt(
                        itemId = item.id,
                        status = SendStatus.UNCERTAIN,
                        errorMessage = error.message ?: "短信提交被意外中断",
                    )
                },
            )
            onProgress(repository.sendDao.itemsOnce(taskId))
            if (sendIntervalMillis > 0L && repository.hasPending(taskId)) {
                waitBetweenMessages(sendIntervalMillis)
            }
        }
        repository.completeTaskIfTerminal(taskId)
        onProgress(repository.sendDao.itemsOnce(taskId))
    }

    private companion object {
        const val SUBMISSION_TIMEOUT_MILLIS = 120_000L
    }
}
