package com.local.bulksms.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.model.SendStatus
import com.local.bulksms.model.WorkspaceSnapshot
import java.util.UUID
import org.json.JSONArray

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

@Entity(tableName = "workspace")
data class WorkspaceEntity(
    @PrimaryKey val id: String = CURRENT_ID,
    val importId: String,
    val rawRowsJson: String,
    val detectedHeader: Boolean,
    val firstRowIsHeader: Boolean,
    val phoneColumnIndex: Int?,
    val backupPhoneColumnIndex: Int? = null,
    val selectedTemplateId: String?,
    val selectedTemplateName: String,
    val selectedTemplateBody: String,
    val selectedSubscriptionId: Int?,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toSnapshot(): WorkspaceSnapshot = WorkspaceSnapshot(
        importId = importId,
        rawRows = decodeRows(rawRowsJson),
        detectedHeader = detectedHeader,
        firstRowIsHeader = firstRowIsHeader,
        phoneColumnIndex = phoneColumnIndex,
        backupPhoneColumnIndex = backupPhoneColumnIndex,
        selectedTemplateId = selectedTemplateId,
        selectedTemplateName = selectedTemplateName,
        selectedTemplateBody = selectedTemplateBody,
        selectedSubscriptionId = selectedSubscriptionId,
    )

    companion object {
        const val CURRENT_ID = "current"

        fun fromSnapshot(snapshot: WorkspaceSnapshot, updatedAt: Long = System.currentTimeMillis()) =
            WorkspaceEntity(
                importId = snapshot.importId,
                rawRowsJson = encodeRows(snapshot.rawRows),
                detectedHeader = snapshot.detectedHeader,
                firstRowIsHeader = snapshot.firstRowIsHeader,
                phoneColumnIndex = snapshot.phoneColumnIndex,
                backupPhoneColumnIndex = snapshot.backupPhoneColumnIndex,
                selectedTemplateId = snapshot.selectedTemplateId,
                selectedTemplateName = snapshot.selectedTemplateName,
                selectedTemplateBody = snapshot.selectedTemplateBody,
                selectedSubscriptionId = snapshot.selectedSubscriptionId,
                updatedAt = updatedAt,
            )

        private fun encodeRows(rows: List<List<String>>): String = JSONArray().apply {
            rows.forEach { row ->
                put(JSONArray().apply { row.forEach(::put) })
            }
        }.toString()

        private fun decodeRows(json: String): List<List<String>> {
            val rows = JSONArray(json)
            return List(rows.length()) { rowIndex ->
                val row = rows.getJSONArray(rowIndex)
                List(row.length()) { columnIndex -> row.getString(columnIndex) }
            }
        }
    }
}

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
    val backupPhoneNumber: String = "",
    val generatedBody: String,
    val currentBody: String,
    val syncWithTable: Boolean = true,
    val manuallyEdited: Boolean = false,
    /** Stored through [AppTypeConverters] so the rendering context survives a restart. */
    val columnNames: List<String>,
    val phoneColumnIndex: Int?,
    val backupPhoneColumnIndex: Int? = null,
) {
    fun toMessageDraft(): MessageDraft = MessageDraft(
        rowId = rowId,
        phoneNumber = phoneNumber,
        backupPhoneNumber = backupPhoneNumber,
        generatedBody = generatedBody,
        currentBody = currentBody,
        syncWithTable = syncWithTable,
        manuallyEdited = manuallyEdited,
        columnNames = columnNames,
        phoneColumnIndex = phoneColumnIndex?.takeIf { it in columnNames.indices },
        backupPhoneColumnIndex = backupPhoneColumnIndex
            ?.takeIf { it in columnNames.indices && it != phoneColumnIndex },
    )

    companion object {
        fun fromDraft(importId: String, draft: MessageDraft, id: String = "$importId:${draft.rowId}"):
            MessageDraftEntity = MessageDraftEntity(
                id = id,
                importId = importId,
                rowId = draft.rowId,
                phoneNumber = draft.phoneNumber,
                backupPhoneNumber = draft.backupPhoneNumber,
                generatedBody = draft.generatedBody,
                currentBody = draft.currentBody,
                syncWithTable = draft.syncWithTable,
                manuallyEdited = draft.manuallyEdited,
                columnNames = draft.columnNames,
                phoneColumnIndex = draft.phoneColumnIndex,
                backupPhoneColumnIndex = draft.backupPhoneColumnIndex,
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
