package com.local.bulksms.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendStatus
import java.util.UUID

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

/** The durable envelope for one imported table. */
@Entity(
    tableName = "import_tasks",
    indices = [Index("createdAt")],
)
data class ImportTaskEntity(
    @PrimaryKey val id: String,
    val sourceName: String? = null,
    val firstRowIsHeader: Boolean = false,
    val phoneColumnIndex: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "message_drafts",
    indices = [
        Index("importId"),
        Index(value = ["importId", "rowId"], unique = true),
    ],
)
data class MessageDraftEntity(
    @PrimaryKey val id: String,
    val importId: String,
    val rowId: Long,
    val phoneNumber: String,
    val generatedBody: String,
    val currentBody: String,
    val syncWithTable: Boolean = true,
    val manuallyEdited: Boolean = false,
    /** Stored through [AppTypeConverters] so the rendering context survives a restart. */
    val columnNames: List<String>,
    val phoneColumnIndex: Int?,
) {
    fun toMessageDraft(): MessageDraft = MessageDraft(
        rowId = rowId,
        phoneNumber = phoneNumber,
        generatedBody = generatedBody,
        currentBody = currentBody,
        syncWithTable = syncWithTable,
        manuallyEdited = manuallyEdited,
        columnNames = columnNames,
        phoneColumnIndex = phoneColumnIndex,
    )

    companion object {
        fun fromDraft(importId: String, draft: MessageDraft, id: String = "$importId:${draft.rowId}"):
            MessageDraftEntity = MessageDraftEntity(
                id = id,
                importId = importId,
                rowId = draft.rowId,
                phoneNumber = draft.phoneNumber,
                generatedBody = draft.generatedBody,
                currentBody = draft.currentBody,
                syncWithTable = draft.syncWithTable,
                manuallyEdited = draft.manuallyEdited,
                columnNames = draft.columnNames,
                phoneColumnIndex = draft.phoneColumnIndex,
            )
    }
}

@Entity(
    tableName = "send_tasks",
    indices = [Index("importId"), Index("createdAt")],
)
data class SendTaskEntity(
    @PrimaryKey val id: String,
    val importId: String,
    val simSubscriptionId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(
    tableName = "send_items",
    foreignKeys = [
        ForeignKey(
            entity = SendTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index(value = ["taskId", "ordinal"], unique = true)],
)
data class SendItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val ordinal: Int,
    val phoneNumber: String,
    val body: String,
    val status: SendStatus,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
)

@Entity(
    tableName = "send_attempts",
    foreignKeys = [
        ForeignKey(
            entity = SendItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index(value = ["itemId", "attemptNumber"], unique = true)],
)
data class SendAttemptEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val attemptNumber: Int,
    val status: SendStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
)

internal fun newEntityId(): String = UUID.randomUUID().toString()
