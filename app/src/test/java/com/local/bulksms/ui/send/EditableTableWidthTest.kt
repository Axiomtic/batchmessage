package com.local.bulksms.ui.send

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTableWidthTest {
    @Test
    fun elevenDigitPhoneNumberGetsEnoughRoomForTextAndPadding() {
        val width = contentAwareColumnWidth(listOf("电话", "13800138000"))

        assertTrue("手机号列宽应至少为 116dp，实际为 $width", width >= 116.dp)
    }
}
