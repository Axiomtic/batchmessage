package com.local.bulksms.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.bulksms.model.SendStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY updatedAt DESC, id")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun byId(id: String): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<TemplateEntity>)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_tasks ORDER BY createdAt DESC, id")
    fun observeAll(): Flow<List<ImportTaskEntity>>

    @Query("SELECT * FROM import_tasks WHERE id = :id")
    suspend fun byId(id: String): ImportTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ImportTaskEntity)

    @Query("DELETE FROM import_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace WHERE id = 'current'")
    suspend fun current(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM message_drafts WHERE importId = :importId ORDER BY rowId, id")
    fun byImport(importId: String): Flow<List<MessageDraftEntity>>

    @Query("SELECT * FROM message_drafts WHERE importId = :importId ORDER BY rowId, id")
    suspend fun byImportOnce(importId: String): List<MessageDraftEntity>

    @Query("SELECT * FROM message_drafts WHERE id = :id")
    suspend fun byId(id: String): MessageDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: MessageDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(drafts: List<MessageDraftEntity>)

    @Query("DELETE FROM message_drafts WHERE importId = :importId")
    suspend fun deleteByImport(importId: String)
}

@Dao
interface SendDao {
    @Query("SELECT * FROM send_tasks WHERE id = :id")
    fun task(id: String): Flow<SendTaskEntity?>

    @Query("SELECT * FROM send_tasks WHERE id = :id")
    suspend fun taskOnce(id: String): SendTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: SendTaskEntity)

    @Query("UPDATE send_tasks SET completedAt = :completedAt WHERE id = :taskId")
    suspend fun completeTask(taskId: String, completedAt: Long): Int

    @Query("SELECT * FROM send_items WHERE taskId = :taskId ORDER BY ordinal")
    fun items(taskId: String): Flow<List<SendItemEntity>>

    @Query("SELECT * FROM send_items WHERE taskId = :taskId ORDER BY ordinal")
    suspend fun itemsOnce(taskId: String): List<SendItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<SendItemEntity>)

    @Query("SELECT * FROM send_items WHERE id = :id")
    suspend fun item(id: String): SendItemEntity?

    @Query("SELECT * FROM send_items WHERE taskId = :taskId AND status = 'PENDING' ORDER BY ordinal LIMIT 1")
    suspend fun nextPending(taskId: String): SendItemEntity?

    /** The affected-row count is the atomic claim result. */
    @Query("UPDATE send_items SET status = 'SUBMITTING' WHERE id = :id AND status = 'PENDING'")
    suspend fun claimPending(id: String): Int

    @Query("UPDATE send_items SET status = :status, errorCode = :errorCode, errorMessage = :errorMessage WHERE id = :id AND status = 'SUBMITTING'")
    suspend fun completeItem(
        id: String,
        status: SendStatus,
        errorCode: Int?,
        errorMessage: String?,
    ): Int

    @Query("UPDATE send_items SET status = 'UNCERTAIN' WHERE taskId = :taskId AND status = 'SUBMITTING'")
    suspend fun markInterruptedUncertain(taskId: String): Int

    @Query("UPDATE send_items SET status = 'CANCELLED' WHERE taskId = :taskId AND status = 'PENDING'")
    suspend fun cancelPending(taskId: String): Int

    @Query("SELECT COUNT(*) FROM send_items WHERE taskId = :taskId AND status = 'PENDING'")
    suspend fun pendingCount(taskId: String): Int

    @Query("SELECT * FROM send_attempts WHERE itemId = :itemId ORDER BY attemptNumber DESC LIMIT 1")
    suspend fun latestAttempt(itemId: String): SendAttemptEntity?

    @Query("SELECT COALESCE(MAX(attemptNumber), 0) FROM send_attempts WHERE itemId = :itemId")
    suspend fun latestAttemptNumber(itemId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(attempt: SendAttemptEntity)

    @Query("UPDATE send_attempts SET status = :status, finishedAt = :finishedAt, errorCode = :errorCode, errorMessage = :errorMessage WHERE id = :id AND status = 'SUBMITTING'")
    suspend fun completeAttempt(
        id: String,
        status: SendStatus,
        finishedAt: Long,
        errorCode: Int?,
        errorMessage: String?,
    ): Int

    @Query("UPDATE send_attempts SET status = 'UNCERTAIN', finishedAt = :finishedAt WHERE itemId IN (SELECT id FROM send_items WHERE taskId = :taskId) AND status = 'SUBMITTING'")
    suspend fun markInterruptedAttempts(taskId: String, finishedAt: Long): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM send_history ORDER BY completedAt DESC, id")
    fun observeAll(): Flow<List<SendHistoryEntity>>

    @Query("SELECT * FROM send_history WHERE id = :id")
    suspend fun byId(id: String): SendHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SendHistoryEntity)
}
