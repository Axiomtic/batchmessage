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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            showAvailable = state.showAvailable,
            showUnavailable = state.showUnavailable,
        )
        MessageReviewList(
            drafts = state.drafts,
            selectedRowIds = state.selectedDraftRowIds,
            enabled = enabled,
            onSelectionChanged = onSelectionChanged,
            showAvailable = state.showAvailable,
            showUnavailable = state.showUnavailable,
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
    draftsStale: Boolean = false,
    onRefresh: () -> Unit = {},
    showAvailable: Boolean = true,
    showUnavailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val draftIds = drafts.mapTo(mutableSetOf()) { it.rowId }
    val selectedCount = selectedRowIds.count { it in draftIds }
    val toggleState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == drafts.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val stats = draftPhoneStats(drafts, showAvailable, showUnavailable)
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
                IconButton(
                    onClick = onRefresh,
                    enabled = enabled,
                    modifier = Modifier.testTag("refresh-preview"),
                ) {
                    Icon(
                        painter = painterResource(BulkSmsIcons.Refresh),
                        contentDescription = "刷新预览",
                        tint = if (draftsStale) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (draftsStale) {
                Text(
                    "预览未更新，点击刷新以同步数据和模板",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
    showAvailable: Boolean = true,
    showUnavailable: Boolean = true,
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
                showAvailable = showAvailable,
                showUnavailable = showUnavailable,
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
    showAvailable: Boolean = true,
    showUnavailable: Boolean = true,
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
                val rawPrimary = PhoneNumberChecker.extractPhoneNumbers(draft.phoneNumber)
                val primaryNumbers = rawPrimary.filter {
                    PhoneNumberChecker.isVisible(it, showAvailable, showUnavailable)
                }
                val rawBackup = PhoneNumberChecker.extractPhoneNumbers(draft.backupPhoneNumber)
                val backupNumbers = rawBackup.filter {
                    PhoneNumberChecker.isVisible(it, showAvailable, showUnavailable)
                }
                val primaryLabel = when {
                    primaryNumbers.isNotEmpty() -> primaryNumbers.joinToString("、")
                    rawPrimary.isNotEmpty() -> "已隐藏"
                    else -> "未设置"
                }
                val primaryColor = when {
                    primaryNumbers.isNotEmpty() -> availablePhoneColor()
                    rawPrimary.isNotEmpty() -> availablePhoneColor().copy(alpha = 0.45f)
                    else -> emptyPhoneColor()
                }
                Text(
                    "$ordinal · 主:$primaryLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                )
                if (backupNumbers.isNotEmpty() || rawBackup.isNotEmpty()) {
                    Text(
                        if (backupNumbers.isNotEmpty()) {
                            "备:${backupNumbers.joinToString("、")}"
                        } else {
                            "备:已隐藏"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (backupNumbers.isNotEmpty()) {
                            availablePhoneColor()
                        } else {
                            availablePhoneColor().copy(alpha = 0.45f)
                        },
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

private data class DraftPhoneStats(val available: Int, val invalid: Int, val empty: Int) {
    val total: Int get() = available + invalid + empty
}

private fun draftPhoneStats(
    drafts: List<MessageDraft>,
    showAvailable: Boolean,
    showUnavailable: Boolean,
): DraftPhoneStats {
    var available = 0
    var invalid = 0
    var empty = 0
    for (draft in drafts) {
        val cells = listOf(draft.phoneNumber, draft.backupPhoneNumber)
        val numbers = cells.flatMap {
            PhoneNumberChecker.visibleNumbers(it, showAvailable, showUnavailable)
        }
        for (number in numbers) {
            if (PhoneNumberChecker.availability(number) == PhoneAvailability.AVAILABLE) {
                available++
            } else {
                invalid++
            }
        }
        if (numbers.isEmpty() && cells.all(String::isBlank)) empty++
    }
    return DraftPhoneStats(available, invalid, empty)
}
