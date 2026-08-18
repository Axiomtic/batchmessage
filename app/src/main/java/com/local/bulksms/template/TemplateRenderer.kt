package com.local.bulksms.template

import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.MessageDraft

class TemplateRenderer(
    private val columns: List<DynamicColumn> = emptyList(),
    private val phoneColumnIndex: Int? = null,
    private val backupPhoneColumnIndex: Int? = null,
) {
    constructor(table: ImportedTable) : this(
        table.columns,
        table.phoneColumnIndex,
        table.backupPhoneColumnIndex,
    )

    constructor(
        columnNames: Collection<String>,
        phoneColumnIndex: Int? = null,
        backupPhoneColumnIndex: Int? = null,
    ) : this(
        columnNames.mapIndexed(::DynamicColumn),
        phoneColumnIndex,
        backupPhoneColumnIndex,
    )

    private val token = Regex("\\{([^{}]+)\\}")

    private fun String.variableKey(): String = trim().uppercase()

    fun validate(template: String, columns: List<String>): Set<String> {
        val known = columns.mapTo(mutableSetOf()) { it.variableKey() }
        return token.findAll(template)
            .map { it.groupValues[1].variableKey() }
            .filterNot(known::contains)
            .toSet()
    }

    fun validate(template: String): Set<String> =
        validate(template, columns.map(DynamicColumn::name))

    fun render(template: String, values: Map<String, String>): String {
        val normalized = values.mapKeys { (key, _) -> key.variableKey() }
        return token.replace(template) { match ->
            normalized.getValue(match.groupValues[1].variableKey())
        }
    }

    fun renderDraft(row: DynamicRow, template: String): MessageDraft {
        require(columns.isNotEmpty()) { "渲染草稿需要动态列" }
        return renderDraftWithColumns(row, template, columns, phoneColumnIndex, backupPhoneColumnIndex)
    }

    internal fun renderDraftWithColumns(
        row: DynamicRow,
        template: String,
        columns: List<DynamicColumn>,
        phoneColumnIndex: Int?,
        backupPhoneColumnIndex: Int? = null,
    ): MessageDraft {
        val columnNames = columns.map(DynamicColumn::name)
        val missing = validate(template, columnNames)
        require(missing.isEmpty()) { "模板包含不存在的变量: ${missing.joinToString("、")}" }

        val values = columns.mapIndexed { index, column ->
            column.name to row.cells.getOrElse(index) { "" }
        }.toMap()
        val body = render(template, values)
        val phoneNumber = phoneColumnIndex?.let { row.cells.getOrNull(it).orEmpty() }.orEmpty()
        val backupPhoneNumber = backupPhoneColumnIndex?.let { row.cells.getOrNull(it).orEmpty() }.orEmpty()
        return MessageDraft(
            rowId = row.id,
            phoneNumber = phoneNumber,
            backupPhoneNumber = backupPhoneNumber,
            generatedBody = body,
            currentBody = body,
            columnNames = columnNames,
            phoneColumnIndex = phoneColumnIndex,
            backupPhoneColumnIndex = backupPhoneColumnIndex,
        )
    }

    internal fun renderDraft(
        row: DynamicRow,
        template: String,
        columnNames: List<String>,
        phoneColumnIndex: Int?,
        backupPhoneColumnIndex: Int? = null,
    ): MessageDraft = renderDraftWithColumns(
        row = row,
        template = template,
        columns = columnNames.mapIndexed(::DynamicColumn),
        phoneColumnIndex = phoneColumnIndex,
        backupPhoneColumnIndex = backupPhoneColumnIndex,
    )
}

object DraftSynchronizer {
    fun editBody(draft: MessageDraft, body: String): MessageDraft =
        draft.copy(
            currentBody = body,
            syncWithTable = false,
            manuallyEdited = true,
        )

    fun regenerate(
        draft: MessageDraft,
        row: DynamicRow,
        template: String,
    ): MessageDraft {
        if (!draft.syncWithTable) return draft
        return rendererFor(draft).renderDraft(row, template).copy(
            syncWithTable = true,
            manuallyEdited = draft.manuallyEdited,
        )
    }

    fun regenerate(
        draft: MessageDraft,
        row: DynamicRow,
        template: String,
        renderer: TemplateRenderer,
    ): MessageDraft {
        if (!draft.syncWithTable) return draft
        return renderer.renderDraft(row, template).copy(
            syncWithTable = true,
            manuallyEdited = draft.manuallyEdited,
        )
    }

    fun regenerate(
        draft: MessageDraft,
        row: DynamicRow,
        template: String,
        columnNames: List<String>,
        phoneColumnIndex: Int? = draft.phoneColumnIndex,
        backupPhoneColumnIndex: Int? = draft.backupPhoneColumnIndex,
    ): MessageDraft {
        if (!draft.syncWithTable) return draft
        return rendererFor(columnNames, phoneColumnIndex, backupPhoneColumnIndex)
            .renderDraft(row, template).copy(
                syncWithTable = true,
                manuallyEdited = draft.manuallyEdited,
            )
    }

    fun setSynced(
        draft: MessageDraft,
        synced: Boolean,
        row: DynamicRow,
        template: String,
    ): MessageDraft {
        if (!synced) return draft.copy(syncWithTable = false)
        return rendererFor(draft).renderDraft(row, template).copy(
            syncWithTable = true,
            manuallyEdited = draft.manuallyEdited,
        )
    }

    fun setSynced(
        draft: MessageDraft,
        synced: Boolean,
        row: DynamicRow,
        template: String,
        renderer: TemplateRenderer,
    ): MessageDraft {
        if (!synced) return draft.copy(syncWithTable = false)
        return renderer.renderDraft(row, template).copy(
            syncWithTable = true,
            manuallyEdited = draft.manuallyEdited,
        )
    }

    fun setSynced(
        draft: MessageDraft,
        synced: Boolean,
        row: DynamicRow,
        template: String,
        columnNames: List<String>,
        phoneColumnIndex: Int? = draft.phoneColumnIndex,
        backupPhoneColumnIndex: Int? = draft.backupPhoneColumnIndex,
    ): MessageDraft {
        if (!synced) return draft.copy(syncWithTable = false)
        return rendererFor(columnNames, phoneColumnIndex, backupPhoneColumnIndex)
            .renderDraft(row, template).copy(
                syncWithTable = true,
                manuallyEdited = draft.manuallyEdited,
            )
    }

    private fun rendererFor(draft: MessageDraft): TemplateRenderer =
        rendererFor(draft.columnNames, draft.phoneColumnIndex, draft.backupPhoneColumnIndex)

    private fun rendererFor(
        columnNames: List<String>,
        phoneColumnIndex: Int?,
        backupPhoneColumnIndex: Int?,
    ): TemplateRenderer =
        TemplateRenderer(
            columns = columnNames.mapIndexed(::DynamicColumn),
            phoneColumnIndex = phoneColumnIndex,
            backupPhoneColumnIndex = backupPhoneColumnIndex,
        )
}
