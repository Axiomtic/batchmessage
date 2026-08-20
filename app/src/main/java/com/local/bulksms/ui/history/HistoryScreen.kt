package com.local.bulksms.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.bulksms.data.SendHistoryEntity
import com.local.bulksms.importdata.ExcelExporter
import com.local.bulksms.ui.components.RoundedIconAction
import com.local.bulksms.ui.icons.BulkSmsIcons
import com.local.bulksms.ui.send.contentAwareColumnWidth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<SendHistoryEntity>,
    onExport: (SendHistoryEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = history.firstOrNull { it.id == selectedId }

    if (selected != null) {
        BackHandler { selectedId = null }
        HistoryDetail(
            entry = selected,
            onExport = { onExport(selected) },
            onBack = { selectedId = null },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("history-back")) {
                Text(
                    "←",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("历史记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        if (history.isEmpty()) {
            Text(
                "暂无发送历史",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { entry ->
                    HistoryItem(entry = entry, onClick = { selectedId = entry.id })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: SendHistoryEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .testTag("history-item-${entry.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                formatTime(entry.completedAt),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "SIM：${entry.simLabel.ifBlank { "未知" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "成功 ${entry.succeeded} · 失败 ${entry.failed} · 共 ${entry.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryDetail(
    entry: SendHistoryEntity,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = remember(entry.id) { ExcelExporter.snapshotOf(entry) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("history-detail-back")) {
                Text(
                    "←",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("发送详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(formatTime(entry.completedAt), style = MaterialTheme.typography.titleSmall)
                Text(
                    "SIM：${entry.simLabel.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "成功 ${entry.succeeded} · 失败 ${entry.failed} · 共 ${entry.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundedIconAction(
                        iconRes = BulkSmsIcons.Share,
                        contentDescription = "导出历史",
                        onClick = onExport,
                        modifier = Modifier.testTag("history-export-${entry.id}"),
                    )
                }
            }
        }

        HistoryTable(snapshot = snapshot, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HistoryTable(
    snapshot: ExcelExporter.HistorySnapshot,
    modifier: Modifier = Modifier,
) {
    val headers = snapshot.headerNames
    val rows = snapshot.exportRows
    val widths = remember(headers, rows) {
        headers.mapIndexed { index, header ->
            contentAwareColumnWidth(
                listOf(header) + rows.map { it.getOrNull(index).orEmpty() },
            )
        }
    }
    val contentWidth = widths.fold(0.dp) { total, width -> total + width }
    val headerColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(contentWidth)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
        ) {
            Row {
                headers.forEachIndexed { index, header ->
                    Box(
                        modifier = Modifier
                            .width(widths[index])
                            .height(38.dp)
                            .background(headerColor)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            LazyColumn {
                items(rows, key = { index -> index }) { row ->
                    Row {
                        row.forEachIndexed { index, cell ->
                            Box(
                                modifier = Modifier
                                    .width(widths[index])
                                    .height(36.dp)
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Text(
                                    cell,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
