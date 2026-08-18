package com.local.bulksms.importdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsImporterTest {

    @Test
    fun readsSharedStringsAndNumbersFromBiff8Workbook() {
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.codepage(0x04B0),
                XlsFixture.sst(listOf("手机号", "姓名", "13800138000", "张三")),
                XlsFixture.boundsheet("Sheet1"),
                XlsFixture.eof(),
            ),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.labelSst(0, 0, 0),
                XlsFixture.labelSst(0, 1, 1),
                XlsFixture.labelSst(1, 0, 2),
                XlsFixture.labelSst(1, 1, 3),
                XlsFixture.eof(),
            ),
        )

        assertEquals(
            listOf(
                listOf("手机号", "姓名"),
                listOf("13800138000", "张三"),
            ),
            XlsImporter().import(XlsFixture.ole2(workbook).inputStream()).rows,
        )
    }

    @Test
    fun readsNumberRkAndMulRkCells() {
        val workbook = XlsFixture.workbook(
            globals = listOf(XlsFixture.bof(0x0005), XlsFixture.boundsheet("Data"), XlsFixture.eof()),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.number(0, 0, 120.0),
                XlsFixture.rk(0, 1, 42 shl 2),
                XlsFixture.mulrk(1, 0, listOf(0 to (7 shl 2), 0 to (30 shl 2)), 1),
                XlsFixture.eof(),
            ),
        )

        assertEquals(
            listOf(
                listOf("120", "42"),
                listOf("7", "30"),
            ),
            XlsImporter().import(XlsFixture.ole2(workbook).inputStream()).rows,
        )
    }

    @Test
    fun readsFormulaCachedResultsIncludingStringResults() {
        val workbook = XlsFixture.workbook(
            globals = listOf(XlsFixture.bof(0x0005), XlsFixture.boundsheet("Data"), XlsFixture.eof()),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.formulaDouble(0, 0, 2.5),
                XlsFixture.formulaString(0, 1),
                XlsFixture.stringResult("缓存字符串"),
                XlsFixture.formulaBoolean(1, 0, true),
                XlsFixture.formulaError(1, 1, 0x07),
                XlsFixture.eof(),
            ),
        )

        assertEquals(
            listOf(
                listOf("2.5", "缓存字符串"),
                listOf("TRUE", "#DIV/0!"),
            ),
            XlsImporter().import(XlsFixture.ole2(workbook).inputStream()).rows,
        )
    }

    @Test
    fun readsDatesUsingNumberFormats() {
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.format(14, "yyyy-mm-dd"),
                XlsFixture.xf(14), // cellXf 0 -> format 14
                XlsFixture.boundsheet("Data"),
                XlsFixture.eof(),
            ),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.numberWithXf(0, 0, 0, 45292.0),
                XlsFixture.eof(),
            ),
        )

        assertEquals(
            listOf(listOf("2024-01-01")),
            XlsImporter().import(XlsFixture.ole2(workbook).inputStream()).rows,
        )
    }

    @Test
    fun selectsFirstVisibleSheetAndSkipsHiddenOnes() {
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.sst(listOf("hidden", "visible")),
                XlsFixture.boundsheet("HiddenSheet", hidden = true),
                XlsFixture.boundsheet("VisibleSheet", hidden = false),
                XlsFixture.eof(),
            ),
            hiddenSheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.labelSst(0, 0, 0),
                XlsFixture.eof(),
            ),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.labelSst(0, 0, 1),
                XlsFixture.eof(),
            ),
        )

        assertEquals(
            listOf(listOf("visible")),
            XlsImporter().import(XlsFixture.ole2(workbook).inputStream()).rows,
        )
    }

    @Test
    fun rejectsNonOle2Input() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            XlsImporter().import("not an xls file".byteInputStream())
        }
        assertTrue(failure.message.orEmpty().contains("无法识别"))
    }

    @Test
    fun importsMoreThanOneHundredRows() {
        val cells = (0..150).map { row ->
            XlsFixture.labelSst(row, 0, 0)
        }
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.sst(listOf("x")),
                XlsFixture.boundsheet("Data"),
                XlsFixture.eof(),
            ),
            sheet = listOf(XlsFixture.bof(0x0010)) + cells + listOf(XlsFixture.eof()),
        )

        val raw = XlsImporter().import(XlsFixture.ole2(workbook).inputStream())
        assertEquals(151, raw.rows.size)
    }
}
