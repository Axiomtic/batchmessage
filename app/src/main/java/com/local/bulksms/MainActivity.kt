package com.local.bulksms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.bulksms.ui.send.ImportScreen
import com.local.bulksms.ui.send.SendFlowViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val sendFlowViewModel: SendFlowViewModel = viewModel()
                val state by sendFlowViewModel.state.collectAsState()
                ImportScreen(
                    state = state,
                    onClipboardImported = sendFlowViewModel::importClipboard,
                    onXlsxImported = sendFlowViewModel::importXlsx,
                    onHeaderChanged = sendFlowViewModel::setFirstRowIsHeader,
                    onCellChanged = { edit ->
                        sendFlowViewModel.editCell(edit.rowId, edit.columnIndex, edit.value)
                    },
                    onPhoneColumnSelected = sendFlowViewModel::selectPhoneColumn,
                )
            }
        }
    }
}
