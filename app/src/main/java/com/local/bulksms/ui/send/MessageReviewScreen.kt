package com.local.bulksms.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.bulksms.model.MessageDraft

@Composable
fun MessageReviewScreen(
    state: SendFlowUiState,
    onEdit: (rowId: Long, body: String) -> Unit,
    onSyncChanged: (rowId: Long, synced: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("确认短信", style = MaterialTheme.typography.headlineSmall)
            Text(
                "共 ${state.drafts.size} 条，可逐条修改。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        MessageReviewList(
            drafts = state.drafts,
            onEdit = onEdit,
            onSyncChanged = onSyncChanged,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MessageReviewList(
    drafts: List<MessageDraft>,
    onEdit: (rowId: Long, body: String) -> Unit,
    onSyncChanged: (rowId: Long, synced: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(drafts, key = { _, draft -> draft.rowId }) { index, draft ->
            MessageReviewItem(
                ordinal = index + 1,
                draft = draft,
                onBodyChanged = { onEdit(draft.rowId, it) },
                onSyncChanged = { onSyncChanged(draft.rowId, it) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun MessageReviewItem(
    ordinal: Int,
    draft: MessageDraft,
    onBodyChanged: (String) -> Unit,
    onSyncChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = draft.currentBody,
            onValueChange = onBodyChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("message-body-${draft.rowId}"),
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$ordinal · ${maskPhone(draft.phoneNumber)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier
                    .semantics { contentDescription = "与表同步" }
                    .toggleable(
                        value = draft.syncWithTable,
                        role = Role.Checkbox,
                        onValueChange = onSyncChanged,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = draft.syncWithTable, onCheckedChange = null)
            }
        }
    }
}

private fun maskPhone(phone: String): String = when {
    phone.length >= 7 -> phone.take(3) + "****" + phone.takeLast(4)
    phone.isBlank() -> "未设置号码"
    else -> phone
}
