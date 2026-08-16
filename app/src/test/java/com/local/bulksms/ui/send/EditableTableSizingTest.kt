package com.local.bulksms.ui.send

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTableSizingTest {
    @Test
    fun longerContentGetsMoreWidthWithinBounds() {
        val short = contentAwareColumnWidth(listOf("名字", "张三"))
        val long = contentAwareColumnWidth(listOf("备注", "这是一段明显更长的内容"))

        assertTrue(long > short)
        assertEquals(76.dp, contentAwareColumnWidth(listOf("1")))
        assertEquals(240.dp, contentAwareColumnWidth(listOf("超长".repeat(100))))
    }
}
