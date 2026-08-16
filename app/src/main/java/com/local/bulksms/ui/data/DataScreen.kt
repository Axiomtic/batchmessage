package com.local.bulksms.ui.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.send.SendFlowUiState

@Composable
fun DataScreen(state: SendFlowUiState, callbacks: BulkSmsCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("数据", style = MaterialTheme.typography.headlineSmall)
        Text("导入数据", style = MaterialTheme.typography.titleMedium)
        Text("${state.table?.rows?.size ?: 0} 行")
    }
}
