package com.local.bulksms.ui.template

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun TemplateScreen(
    state: TemplateUiState,
    availableVariables: List<String>,
    onSelect: (String) -> Unit,
    onStartNew: () -> Unit,
    onNameChanged: (String) -> Unit,
    onBodyChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bodyValue by remember {
        mutableStateOf(
            TextFieldValue(state.editorBody, selection = TextRange(state.editorBody.length)),
        )
    }
    LaunchedEffect(state.selectedTemplateId, state.editorBody) {
        if (bodyValue.text != state.editorBody) {
            bodyValue = TextFieldValue(
                state.editorBody,
                selection = TextRange(state.editorBody.length),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("短信模板", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onStartNew) { Text("新建") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.templates.forEach { template ->
                OutlinedButton(onClick = { onSelect(template.id) }) {
                    Text(template.name)
                }
            }
        }
        OutlinedTextField(
            value = state.editorName,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模板名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = bodyValue,
            onValueChange = { value ->
                bodyValue = value
                onBodyChanged(value.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("template-body"),
            label = { Text("短信正文") },
            minLines = 5,
        )
        Text("可用变量", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableVariables.forEach { variable ->
                OutlinedButton(
                    onClick = {
                        val token = "{$variable}"
                        val start = bodyValue.selection.min
                        val end = bodyValue.selection.max
                        val updated = bodyValue.text.replaceRange(start, end, token)
                        val cursor = start + token.length
                        bodyValue = TextFieldValue(updated, selection = TextRange(cursor))
                        onBodyChanged(updated)
                    },
                    modifier = Modifier.testTag("variable-$variable"),
                ) {
                    Text("{$variable}")
                }
            }
        }
        state.validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave) { Text("保存模板") }
            state.selectedTemplateId?.let { selectedId ->
                OutlinedButton(onClick = { onDelete(selectedId) }) { Text("删除") }
            }
        }
    }
}
