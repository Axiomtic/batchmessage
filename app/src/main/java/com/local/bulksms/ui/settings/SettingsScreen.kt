package com.local.bulksms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowUiState

@Composable
fun SettingsScreen(
    state: SendFlowUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("忽略首行", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后，导入数据的第一行不参与短信生成。列号始终使用 A、B、C。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.table?.firstRowIsHeader == true,
                        onCheckedChange = callbacks.onHeaderModeChanged,
                        modifier = Modifier.testTag("ignore-first-row"),
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "手机号列") {
                state.table?.columns.orEmpty().forEachIndexed { index, column ->
                    ChoiceRow(
                        label = "${column.name} 列",
                        selected = state.selectedPhoneColumn == index,
                        testTag = "phone-column-${column.name}",
                        onClick = { callbacks.onPhoneColumnSelected(index) },
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "发送 SIM") {
                if (state.simOptions.isEmpty()) {
                    Text(
                        "未检测到可用 SIM",
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.simOptions.forEach { sim ->
                        ChoiceRow(
                            label = sim.displayLabel,
                            selected = state.selectedSubscriptionId == sim.subscriptionId,
                            testTag = "sim-${sim.subscriptionId}",
                            onClick = { callbacks.onSubscriptionSelected(sim.subscriptionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
        )
        Text(label)
    }
}
