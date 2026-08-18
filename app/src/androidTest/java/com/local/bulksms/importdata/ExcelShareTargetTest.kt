package com.local.bulksms.importdata

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.bulksms.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExcelShareTargetTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun xlsxAppearsAsAStandardShareTarget() {
        assertTrue(
            resolvesToThisApp(
                Intent(Intent.ACTION_SEND).apply {
                    type = XLSX_MIME
                    putExtra(Intent.EXTRA_STREAM, Uri.parse("content://sender/example.xlsx"))
                },
            ),
        )
    }

    @Test
    fun genericBinaryShareAppearsForCompatibility() {
        assertTrue(
            resolvesToThisApp(
                Intent(Intent.ACTION_SEND).apply {
                    type = GENERIC_BINARY_MIME
                    putExtra(Intent.EXTRA_STREAM, Uri.parse("content://sender/example.xlsx"))
                },
            ),
        )
    }

    @Test
    fun xlsxAppearsInOpenWithChooser() {
        assertTrue(
            resolvesToThisApp(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://sender/example.xlsx"), XLSX_MIME)
                },
            ),
        )
    }

    @Test
    fun unreadableSharedFileShowsAnImportError() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = XLSX_MIME
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://missing/example.xlsx"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithText("无法读取分享的 Excel 文件")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("无法读取分享的 Excel 文件").assertExists()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvesToThisApp(intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == context.packageName }

    private companion object {
        const val XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val GENERIC_BINARY_MIME = "application/octet-stream"
    }
}
