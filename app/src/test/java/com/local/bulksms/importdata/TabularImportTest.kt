package com.local.bulksms.importdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import com.local.bulksms.model.DynamicColumn
import com.local.bulksms.model.DynamicRow
import com.local.bulksms.model.ImportedTable
import com.local.bulksms.model.RawTable

class TabularImportTest {
    @Test
    fun parserTrimsCellsSkipsBlankLinesAndPadsRows() {
        val raw = TabularTextParser.parse(" 13800138000 \t 张三 \r\n\r\n13900139000\t")

        assertEquals(
            listOf(
                listOf("13800138000", "张三"),
                listOf("13900139000", ""),
            ),
            raw.rows,
        )
    }

    @Test
    fun fourConsecutiveSpacesSeparateClipboardCellsLikeATab() {
        val raw = TabularTextParser.parse(
            "姓名    电话    到期日期\n张三    13800138000    2027-01-01",
        )

        assertEquals(
            listOf(
                listOf("姓名", "电话", "到期日期"),
                listOf("张三", "13800138000", "2027-01-01"),
            ),
            raw.rows,
        )
    }

    @Test
    fun parserRejectsClipboardWithoutRows() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            TabularTextParser.parse("\r\n \t \n")
        }

        assertEquals("剪贴板中没有表格数据", failure.message)
    }

    @Test
    fun noHeaderUsesNumberedColumnsAndKeepsFirstRow() {
        val raw = TabularTextParser.parse("13800138000\t张三\r\n13900139000\t李四")
        val table = HeaderDetector.materialize(raw, firstRowIsHeader = false)

        assertEquals(listOf("A", "B"), table.columns.map { it.name })
        assertEquals("13800138000", table.rows.first().cells.first())
        assertEquals(0, PhoneColumnDetector.recommend(table))
    }

    @Test
    fun headerTextBecomesColumnNamesAndDuplicatesBecomeUnique() {
        val raw = TabularTextParser.parse("姓名\t姓名\t\n张三\tA\t1")
        val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)

        assertEquals(listOf("姓名", "姓名2", "C"), table.columns.map { it.name })
    }

    @Test
    fun blankHeadersUseColumnAddressesAndDoNotChangeDataCells() {
        val raw = RawTable(
            rows = listOf(
                listOf("", "姓名", "姓名_2", "姓名"),
                listOf("001", "张三", "旧", "新"),
            ),
        )

        val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)

        assertEquals(listOf("A", "姓名", "姓名_2", "姓名2"), table.columns.map { it.name })
        assertEquals(listOf("001", "张三", "旧", "新"), table.rows.single().cells)
    }

    @Test
    fun headerMaterializationPreservesModeAndAssignsStableIds() {
        val raw = RawTable(
            rows = listOf(
                listOf("手机号", "姓名"),
                listOf("+14155552671", "Alice"),
                listOf("0013800138000", "Bob"),
            ),
        )

        val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)

        assertEquals(
            listOf(DynamicColumn(0, "手机号"), DynamicColumn(1, "姓名")),
            table.columns,
        )
        assertEquals(
            listOf(
                DynamicRow(0, listOf("+14155552671", "Alice")),
                DynamicRow(1, listOf("0013800138000", "Bob")),
            ),
            table.rows,
        )
        assertTrue(table.firstRowIsHeader)
        assertEquals(null, table.phoneColumnIndex)
    }

    @Test
    fun headerNamesAreTrimmedAndDuplicatesGetNumericSuffix() {
        val raw = RawTable(
            rows = listOf(
                listOf("姓名", "姓名 ", "名字"),
                listOf("张三", "李四", "王五"),
            ),
        )
        // "姓名" and "姓名 " trim to the same name, so the second gets a suffix.
        val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)
        assertEquals(listOf("姓名", "姓名2", "名字"), table.columns.map { it.name })
    }

    @Test
    fun exactlyOneHundredDataRowsAreAllowedAfterHeader() {
        val raw = RawTable(
            rows = listOf(listOf("手机号")) + (1..100).map { listOf("1380013%04d".format(it)) },
        )

        val table = HeaderDetector.materialize(raw, firstRowIsHeader = true)

        assertEquals(100, table.rows.size)
    }

    @Test
    fun phoneValidationAllowsInternationalFormattingAndRejectsInvalidCharacters() {
        assertTrue(PhoneColumnDetector.isValid("+1 (415) 555-2671"))
        assertTrue(PhoneColumnDetector.isValid("0013800138000"))
        assertTrue(PhoneColumnDetector.isValid(" 001 3800-138-000 "))
        assertFalse(PhoneColumnDetector.isValid("张三"))
        assertFalse(PhoneColumnDetector.isValid("+1 (415) 555-267x"))
        assertFalse(PhoneColumnDetector.isValid("+"))
    }

    @Test
    fun phoneRecommendationChoosesUniqueHighestRatio() {
        val table = ImportedTable(
            columns = listOf(DynamicColumn(0, "A"), DynamicColumn(1, "B")),
            rows = listOf(
                DynamicRow(0, listOf("+1 (415) 555-2671", "Alice")),
                DynamicRow(1, listOf("0013800138000", "13800138000")),
                DynamicRow(2, listOf("not-a-phone", "Bob")),
            ),
            firstRowIsHeader = false,
        )

        assertEquals(0, PhoneColumnDetector.recommend(table))
    }

    @Test
    fun phoneRecommendationReturnsNullOnTieOrWhenNoColumnIsValid() {
        val table = ImportedTable(
            columns = listOf(DynamicColumn(0, "A"), DynamicColumn(1, "B")),
            rows = listOf(
                DynamicRow(0, listOf("+14155552671", "13800138000")),
                DynamicRow(1, listOf("not-a-phone", "not-a-phone")),
            ),
            firstRowIsHeader = false,
        )
        val invalidTable = table.copy(
            rows = listOf(DynamicRow(0, listOf("Alice", "Bob"))),
        )

        assertEquals(null, PhoneColumnDetector.recommend(table))
        assertEquals(null, PhoneColumnDetector.recommend(invalidTable))
    }

    @Test
    fun moreThanOneHundredDataRowsIsRejected() {
        val text = (1..101).joinToString("\n") { "1380013%04d\t姓名$it".format(it) }
        val raw = TabularTextParser.parse(text)

        assertThrows(ImportLimitExceeded::class.java) {
            HeaderDetector.materialize(raw, firstRowIsHeader = false)
        }
    }
}
