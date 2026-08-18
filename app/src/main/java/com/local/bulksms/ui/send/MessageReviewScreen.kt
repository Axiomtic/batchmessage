package com.local.bulksms.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.importdata.PhoneAvailability
import com.local.bulksms.importdata.PhoneNumberChecker
import com.local.bulksms.model.MessageDraft
import com.local.bulksms.ui.icons.BulkSmsIcons
import com.local.bulksms.ui.theme.availablePhoneColor
import com.local.bulksms.ui.theme.emptyPhoneColor
import com.local.bulksms.ui.theme.invalidPhoneColor

@Composable
fun MessageReviewScreen(
    state: SendFlowUiState,
    enabled: Boolean,
    onSelectionChanged: (rowId: Long, selected: Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageReviewHeader(
            drafts = state.drafts,
            selectedRowIds = state.selectedDraftRowIds,
            enabled = enabled,
            onSelectAll = onSelectAll,
        )
        MessageReviewList(
            drafts = state.drafts,
            selectedRowIds = state.selectedDraftRowIds,
            enabled = enabled,
            onSelectionChanged = onSelectionChanged,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MessageReviewHeader(
    drafts: List<MessageDraft>,
    selectedRowIds: Set<Long>,
    enabled: Boolean,
    onSelectAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draftIds = drafts.mapTo(mutableSetOf()) { it.rowId }
    val selectedCount = selectedRowIds.count { it in draftIds }
    val toggleState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == drafts.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val stats = draftPhoneStats(drafts)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(BulkSmsIcons.Preview),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("短信预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "共 ${drafts.size} 条，已选 $selectedCount 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.total > 0) {
                Text(
                    "号码  有效 ${stats.available} · 无效 ${stats.invalid} · 空 ${stats.empty}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("preview-phone-stats"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("全选", style = MaterialTheme.typography.bodySmall)
            TriStateCheckbox(
                state = toggleState,
                onClick = { onSelectAll(toggleState != ToggleableState.On) },
                enabled = enabled && drafts.isNotEmpty(),
                modifier = Modifier.testTag("select-all-drafts"),
            )
        }
    }
}

@Composable
fun MessageReviewList(
    drafts: List<MessageDraft>,
    selectedRowIds: Set<Long>,
    enabled: Boolean,
    onSelectionChanged: (rowId: Long, selected: Boolean) -> Unit,
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
                selected = draft.rowId in selectedRowIds,
                enabled = enabled,
                onSelectionChanged = { selected -> onSelectionChanged(draft.rowId, selected) },
            )
        }
    }
}

@Composable
private fun MessageReviewItem(
    ordinal: Int,
    draft: MessageDraft,
    selected: Boolean,
    enabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "$ordinal · 主:${draft.phoneNumber.ifBlank { "未设置" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = phoneColor(draft.phoneNumber),
                    fontWeight = FontWeight.SemiBold,
                )
                if (draft.backupPhoneNumber.isNotBlank()) {
                    Text(
                        "备:${draft.backupPhoneNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = phoneColor(draft.backupPhoneNumber),
                    )
                }
                Text(draft.currentBody, style = MaterialTheme.typography.bodyMedium)
            }
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectionChanged,
                enabled = enabled,
                modifier = Modifier.testTag("send-draft-${draft.rowId}"),
            )
        }
    }
}

@Composable
private fun phoneColor(value: String): Color = when (PhoneNumberChecker.availability(value)) {
    PhoneAvailability.AVAILABLE -> availablePhoneColor()
    PhoneAvailability.INVALID -> invalidPhoneColor()
    PhoneAvailability.EMPTY -> emptyPhoneColor()
}

private data class DraftPhoneStats(val available: Int, val invalid: Int, val empty: Int) {
    val total: Int get() = available + invalid + empty
}

private fun draftPhoneStats(drafts: List<MessageDraft>): DraftPhoneStats {
    var available = 0
    var invalid = 0
    var empty = 0
    for (draft in drafts) {
        for (value in listOf(draft.phoneNumber, draft.backupPhoneNumber)) {
            when (PhoneNumberChecker.availability(value)) {
                PhoneAvailability.AVAILABLE -> available++
                PhoneAvailability.INVALID -> invalid++
                PhoneAvailability.EMPTY -> empty++
            }
        }
    }
    return DraftPhoneStats(available, invalid, empty)
}
