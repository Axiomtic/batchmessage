package com.local.bulksms.ui.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.bulksms.ui.BulkSmsCallbacks
import com.local.bulksms.ui.template.TemplateUiState

@Composable
fun SmsScreen(
    state: SendFlowUiState,
    templateState: TemplateUiState,
    callbacks: BulkSmsCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("短信", style = MaterialTheme.typography.headlineSmall)
        Text("短信预览", style = MaterialTheme.typography.titleMedium)
        Text("${state.drafts.size} 条")
    }
}
