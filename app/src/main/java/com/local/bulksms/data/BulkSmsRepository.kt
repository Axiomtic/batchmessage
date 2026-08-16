package com.local.bulksms.data

import androidx.room.withTransaction
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendAttemptResult
import com.local.bulksms.model.SendStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BulkSmsRepository(
    private val database: AppDatabase,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    val templateDao: TemplateDao
        get() = database.templateDao()

    val importDao: ImportDao
        get() = database.importDao()

    val draftDao: DraftDao
        get() = database.draftDao()

    val sendDao: SendDao
        get() = database.sendDao()

    suspend fun saveImport(task: ImportTaskEntity) {
        database.importDao().upsert(task)
    }

    suspend fun saveDraft(importId: String, draft: MessageDraft) {
        database.draftDao().upsert(MessageDraftEntity.fromDraft(importId, draft))
    }

    suspend fun saveDrafts(importId: String, drafts: List<MessageDraft>) {
        database.withTransaction {
            database.draftDao().upsertAll(drafts.map { MessageDraftEntity.fromDraft(importId, it) })
        }
    }

    fun observeDrafts(importId: String): Flow<List<MessageDraft>> =
        database.draftDao().byImport(importId).mapDrafts()

    /**
     * Atomically snapshots the current draft phone/body values into a new queue.
     * Subsequent draft edits cannot change these send items.
     */
    suspend fun freezeQueue(importId: String, simSubscriptionId: Int): String =
        database.withTransaction {
            val drafts = database.draftDao().byImportOnce(importId)
                .sortedWith(compareBy<MessageDraftEntity> { it.rowId }.thenBy { it.id })
            val taskId = idFactory()
            database.sendDao().insertTask(
                SendTaskEntity(
                    id = taskId,
                    importId = importId,
                    simSubscriptionId = simSubscriptionId,
                    createdAt = clock(),
                ),
            )
            database.sendDao().insertItems(
                drafts.mapIndexed { ordinal, draft ->
                    SendItemEntity(
                        id = idFactory(),
                        taskId = taskId,
                        ordinal = ordinal,
                        phoneNumber = draft.phoneNumber,
                        body = draft.currentBody,
                        status = SendStatus.PENDING,
                    )
                },
            )
            taskId
        }

    /**
     * Selects the first pending row and changes it to SUBMITTING in the same
     * transaction. A second caller can never receive the same row.
     */
    suspend fun claimNext(taskId: String): SendItemEntity? = database.withTransaction {
        val candidate = database.sendDao().nextPending(taskId) ?: return@withTransaction null
        if (database.sendDao().claimPending(candidate.id) != 1) return@withTransaction null

        val now = clock()
        val attemptNumber = database.sendDao().latestAttemptNumber(candidate.id) + 1
        database.sendDao().insertAttempt(
            SendAttemptEntity(
                id = idFactory(),
                itemId = candidate.id,
                attemptNumber = attemptNumber,
                status = SendStatus.SUBMITTING,
                startedAt = now,
            ),
        )
        database.sendDao().item(candidate.id)?.copy(status = SendStatus.SUBMITTING)
            ?: candidate.copy(status = SendStatus.SUBMITTING)
    }

    suspend fun completeAttempt(
        itemId: String,
        status: SendStatus,
        errorCode: Int? = null,
        errorMessage: String? = null,
    ): Boolean {
        require(status in setOf(SendStatus.SUBMITTED, SendStatus.FAILED, SendStatus.UNCERTAIN)) {
            "SUBMITTING 只能完成为 SUBMITTED、FAILED 或 UNCERTAIN"
        }
        return database.withTransaction {
            val item = database.sendDao().item(itemId)
            if (item?.status != SendStatus.SUBMITTING) return@withTransaction false
            val now = clock()
            val updated = database.sendDao().completeItem(
                id = itemId,
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
            if (updated != 1) return@withTransaction false

            database.sendDao().latestAttempt(itemId)?.let { attempt ->
                database.sendDao().completeAttempt(
                    id = attempt.id,
                    status = status,
                    finishedAt = now,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            }
            true
        }
    }

    suspend fun completeAttempt(itemId: String, result: SendAttemptResult): Boolean =
        completeAttempt(itemId, result.status, result.errorCode, result.errorMessage)

    suspend fun recoverInterruptedAttempts(taskId: String) {
        database.withTransaction {
            val now = clock()
            database.sendDao().markInterruptedUncertain(taskId)
            database.sendDao().markInterruptedAttempts(taskId, now)
        }
    }

    suspend fun hasPending(taskId: String): Boolean = database.sendDao().pendingCount(taskId) > 0

    suspend fun cancelPending(taskId: String): Int = database.sendDao().cancelPending(taskId)

    suspend fun completeTaskIfTerminal(taskId: String): Boolean = database.withTransaction {
        val items = database.sendDao().itemsOnce(taskId)
        if (items.isEmpty() || items.any { it.status == SendStatus.PENDING || it.status == SendStatus.SUBMITTING }) {
            false
        } else {
            database.sendDao().completeTask(taskId, clock()) == 1
        }
    }
}

private fun Flow<List<MessageDraftEntity>>.mapDrafts(): Flow<List<MessageDraft>> = map { entities ->
    entities.map(MessageDraftEntity::toMessageDraft)
}
