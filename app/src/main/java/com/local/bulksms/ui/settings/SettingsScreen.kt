package com.local.bulksms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.local.bulksms.sms.formatSendIntervalSeconds
import com.local.bulksms.sms.parseSendIntervalMillis
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.icons.BulkSmsIcons
import com.local.bulksms.ui.send.SendFlowUiState
import com.local.bulksms.ui.send.SimDetectionState
import com.local.bulksms.ui.theme.neutralOutlinedTextFieldColors

@Composable
fun SettingsScreen(
    state: SendFlowUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("settings-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(BulkSmsIcons.Settings),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
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
            SettingsGroup(title = "发送间隔", iconRes = BulkSmsIcons.Interval) {
                SendIntervalField(
                    intervalMillis = state.sendIntervalMillis,
                    onIntervalChanged = callbacks.onSendIntervalChanged,
                )
            }
        }
        item {
            SettingsGroup(title = "发送 SIM", iconRes = BulkSmsIcons.Sim) {
                when (state.simDetectionState) {
                    SimDetectionState.PERMISSION_REQUIRED -> {
                        Text(
                            "需要电话权限才能读取 SIM",
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = callbacks.onRequestSimPermission,
                            modifier = Modifier.testTag("grant-sim-permission"),
                        ) { Text("允许读取 SIM") }
                    }
                    SimDetectionState.LOADING -> Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("正在检测 SIM")
                    }
                    SimDetectionState.EMPTY -> {
                        Text(
                            "没有检测到活动 SIM",
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = callbacks.onRefreshSimOptions) { Text("重新检测") }
                    }
                    SimDetectionState.ERROR -> {
                        Text(
                            state.simDetectionError ?: "SIM 检测失败",
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = callbacks.onRefreshSimOptions) { Text("重试") }
                    }
                    SimDetectionState.AVAILABLE -> state.simOptions.forEach { sim ->
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
private fun SendIntervalField(
    intervalMillis: Long,
    onIntervalChanged: (Long) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(formatSendIntervalSeconds(intervalMillis)) }
    val parsed = parseSendIntervalMillis(text)
    LaunchedEffect(intervalMillis) {
        if (parsed != intervalMillis) text = formatSendIntervalSeconds(intervalMillis)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                if (value.length <= 6 && value.matches(Regex("\\d*(\\.\\d*)?"))) {
                    text = value
                    parseSendIntervalMillis(value)?.let(onIntervalChanged)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("send-interval-seconds"),
            label = { Text("间隔时间") },
            suffix = { Text("秒") },
            supportingText = {
                Text(if (parsed == null) "请输入 0–60 秒" else "仅在相邻两条短信之间等待")
            },
            isError = parsed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = neutralOutlinedTextFieldColors(),
        )
    }
}

@Composable
private fun SettingsGroup(title: String, iconRes: Int, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
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
