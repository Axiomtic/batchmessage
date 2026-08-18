package com.local.bulksms.importdata

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelImporterTest {

    @Test
    fun dispatchesZipMagicToTheXlsxReader() {
        val xlsx = xlsxFixture(listOf(listOf("姓名", "13800138000")))
        val raw = ExcelImporter.import(xlsx.inputStream())

        assertEquals(listOf(listOf("姓名", "13800138000")), raw.rows)
    }

    @Test
    fun dispatchesOle2MagicToTheXlsReader() {
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.sst(listOf("姓名", "13800138000")),
                XlsFixture.boundsheet("Data"),
                XlsFixture.eof(),
            ),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.labelSst(0, 0, 0),
                XlsFixture.labelSst(0, 1, 1),
                XlsFixture.eof(),
            ),
        )
        val raw = ExcelImporter.import(XlsFixture.ole2(workbook).inputStream())

        assertEquals(listOf(listOf("姓名", "13800138000")), raw.rows)
    }

    @Test
    fun importsXlsContentEvenWhenCarryingAnXlsxName() {
        // A file whose *content* is BIFF8 must still import through the magic-byte path.
        val workbook = XlsFixture.workbook(
            globals = listOf(
                XlsFixture.bof(0x0005),
                XlsFixture.sst(listOf("a")),
                XlsFixture.boundsheet("Data"),
                XlsFixture.eof(),
            ),
            sheet = listOf(
                XlsFixture.bof(0x0010),
                XlsFixture.labelSst(0, 0, 0),
                XlsFixture.eof(),
            ),
        )
        val raw = ExcelImporter.import(XlsFixture.ole2(workbook).inputStream())

        assertEquals(listOf(listOf("a")), raw.rows)
    }

    @Test
    fun rejectsUnrecognizedContent() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ExcelImporter.import("plain text, not an excel file".byteInputStream())
        }
        assertTrue(failure.message.orEmpty().contains("无法识别"))
    }

    private fun xlsxFixture(rows: List<List<String>>): ByteArray {
        val sheetXml = buildString {
            append(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<sheetData>",
            )
            rows.forEachIndexed { rowIndex, cells ->
                append("<row r=\"${rowIndex + 1}\">")
                cells.forEachIndexed { columnIndex, value ->
                    val ref = "${('A'.code + columnIndex).toChar()}${rowIndex + 1}"
                    append("<c r=\"$ref\" t=\"inlineStr\"><is><t>")
                    append(escapeXml(value))
                    append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val workbookXml =
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        val relsXml =
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
                "Target=\"worksheets/sheet1.xml\"/></Relationships>"

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(workbookXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(relsXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
