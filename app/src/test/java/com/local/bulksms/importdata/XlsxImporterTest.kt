package com.local.bulksms.importdata

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class XlsxImporterTest {
    @Test
    fun readsFirstVisibleSheetAndSharedStrings() {
        val bytes = xlsxFixture(
            sheets = listOf(
                hiddenSheet("Hidden", listOf(listOf(text("should not be read")))),
                visibleSheet(
                    "Data",
                    listOf(
                        listOf(text("手机号"), text("姓名"), text("金额")),
                        listOf(text("13800138000"), inline("张三"), number("120")),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                listOf("手机号", "姓名", "金额"),
                listOf("13800138000", "张三", "120"),
            ),
            XlsxImporter().import(bytes.inputStream()).rows,
        )
    }

    @Test
    fun skipsVisibleNonWorksheetRelationshipBeforeVisibleWorksheet() {
        val bytes = xlsxFixture(
            sheets = listOf(
                nonWorksheetSheet("Chart", "chartsheet"),
                visibleSheet("Data", listOf(listOf(inline("selected")))),
            ),
        )

        assertEquals(
            listOf(listOf("selected")),
            XlsxImporter().import(bytes.inputStream()).rows,
        )
    }

    @Test
    fun readsStrictWorksheetRelationshipAfterSkippingChartAndDialogSheets() {
        val bytes = xlsxFixture(
            sheets = listOf(
                nonWorksheetSheet("Chart", "chartsheet"),
                nonWorksheetSheet("Dialog", "dialogsheet"),
                strictVisibleSheet("Data", listOf(listOf(inline("strict selected")))),
            ),
        )

        assertEquals(
            listOf(listOf("strict selected")),
            XlsxImporter().import(bytes.inputStream()).rows,
        )
    }

    @Test
    fun readsBooleanDatesAndFormulaCachedValues() {
        val bytes = xlsxFixture(
            sheets = listOf(
                visibleSheetXml(
                    "Data",
                    """
                    <sheetData>
                      <row r="1">
                        <c r="A1" s="1"><v>45292</v></c>
                        <c r="B1" t="b"><v>1</v></c>
                        <c r="C1"><f>1+1</f><v>2</v></c>
                        <c r="D1"><f>NOW()</f></c>
                      </row>
                    </sheetData>
                    """.trimIndent(),
                ),
            ),
            styles = """
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cellXfs count="2">
                    <xf numFmtId="0"/>
                    <xf numFmtId="14"/>
                  </cellXfs>
                </styleSheet>
            """.trimIndent(),
        )

        val raw = XlsxImporter().import(bytes.inputStream())

        assertEquals(listOf("2024-01-01", "TRUE", "2", ""), raw.rows.single())
        assertTrue(raw.warnings.any { it.contains("D1") && it.contains("缓存") })
    }

    @Test
    fun readsDateUsingThe1904WorkbookDateSystem() {
        val bytes = xlsxFixture(
            sheets = listOf(
                visibleSheetXml(
                    "Data",
                    "<sheetData><row r=\"1\"><c r=\"A1\" s=\"1\"><v>0</v></c></row></sheetData>",
                ),
            ),
            date1904 = true,
            styles = """
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="14"/></cellXfs>
                </styleSheet>
            """.trimIndent(),
        )

        assertEquals(listOf("1904-01-01"), XlsxImporter().import(bytes.inputStream()).rows.single())
    }

    @Test
    fun recognizesCommonBuiltinDateStyleIds() {
        val bytes = xlsxFixture(
            sheets = listOf(
                visibleSheetXml(
                    "Data",
                    "<sheetData><row r=\"1\"><c r=\"A1\" s=\"1\"><v>1</v></c><c r=\"B1\" s=\"2\"><v>1</v></c></row></sheetData>",
                ),
            ),
            styles = """
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cellXfs count="3">
                    <xf numFmtId="0"/><xf numFmtId="27"/><xf numFmtId="50"/>
                  </cellXfs>
                </styleSheet>
            """.trimIndent(),
        )

        assertEquals(
            listOf("1900-01-01", "1900-01-01"),
            XlsxImporter().import(bytes.inputStream()).rows.single(),
        )
    }

    @Test
    fun fillsMissingCellsFromCellReferences() {
        val bytes = xlsxFixture(
            sheets = listOf(
                visibleSheetXml(
                    "Data",
                    """
                    <sheetData>
                      <row r="1"><c r="A1" t="inlineStr"><is><t>A</t></is></c><c r="C1" t="inlineStr"><is><t>C</t></is></c></row>
                      <row r="2"><c r="B2" t="inlineStr"><is><t>B</t></is></c></row>
                    </sheetData>
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(
            listOf(listOf("A", "", "C"), listOf("", "B", "")),
            XlsxImporter().import(bytes.inputStream()).rows,
        )
    }

    @Test
    fun acceptsOneHundredAndOneRawRowsForHeaderDetection() {
        val rows = (1..101).map { listOf(number("$it")) }
        val raw = XlsxImporter().import(
            xlsxFixture(sheets = listOf(visibleSheet("Data", rows))).inputStream(),
        )

        assertEquals(101, raw.rows.size)
        assertThrows(ImportLimitExceeded::class.java) {
            HeaderDetector.materialize(raw, firstRowIsHeader = false)
        }
        assertEquals(100, HeaderDetector.materialize(raw, firstRowIsHeader = true).rows.size)
    }

    @Test
    fun rejectsUnsupportedOrOversizedWorkbook() {
        assertThrows(ImportLimitExceeded::class.java) {
            XlsxImporter().import(
                xlsxFixture(
                    sheets = listOf(
                        visibleSheet("Data", (1..102).map { listOf(number("$it")) }),
                    ),
                ).inputStream(),
            )
        }
    }

    @Test
    fun rejectsZipPathTraversal() {
        val bytes = zipOf(
            "xl/workbook.xml" to minimalWorkbookXml(),
            "xl/_rels/workbook.xml.rels" to minimalRelationshipsXml(),
            "../outside.xml" to "not allowed",
        )

        assertThrows(IllegalArgumentException::class.java) {
            XlsxImporter().import(bytes.inputStream())
        }
    }

    @Test
    fun rejectsRelationshipTargetTraversalAndAbsolutePath() {
        listOf("../outside.xml", "/outside.xml").forEach { target ->
            val bytes = zipOf(
                "xl/workbook.xml" to minimalWorkbookXml(),
                "xl/_rels/workbook.xml.rels" to minimalRelationshipsXml(target),
            )

            assertThrows(IllegalArgumentException::class.java) {
                XlsxImporter().import(bytes.inputStream())
            }
        }
    }

    @Test
    fun rejectsCumulativeUncompressedZipDataOverEightMib() {
        val chunk = ByteArray(4 * 1024 * 1024 + 1) { 'x'.code.toByte() }
        val bytes = zipOfBytes(
            "first.bin" to chunk,
            "second.bin" to chunk,
        )

        assertThrows(IllegalArgumentException::class.java) {
            XlsxImporter().import(bytes.inputStream())
        }
    }

    @Test
    fun rejectsExternalEntityDeclarations() {
        val workbookXml = """
            <!DOCTYPE workbook [<!ENTITY xxe SYSTEM "file:///tmp/xlsx-secret">]>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="&xxe;" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val bytes = xlsxFixture(
            sheets = listOf(visibleSheet("Data", listOf(listOf(inline("value")))),),
            workbookXmlOverride = workbookXml,
        )

        // External entity references are still refused as part of the XXE hardening.
        assertThrows(IllegalArgumentException::class.java) {
            XlsxImporter().import(bytes.inputStream())
        }
    }

    @Test
    fun toleratesHarmlessInternalEntityDeclarations() {
        val workbookXml = """
            <!DOCTYPE workbook [<!ENTITY boom "expanded">]>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="&boom;" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val bytes = xlsxFixture(
            sheets = listOf(visibleSheet("Data", listOf(listOf(inline("value")))),),
            workbookXmlOverride = workbookXml,
        )

        // A harmless internal DOCTYPE entity (some third-party writers emit these)
        // must not break the import.
        val raw = XlsxImporter().import(bytes.inputStream())
        assertEquals(listOf(listOf("value")), raw.rows)
    }

    @Test
    fun rejectsColumnReferenceBeyondExcelMaximum() {
        val bytes = xlsxFixture(
            sheets = listOf(
                visibleSheetXml(
                    "Data",
                    "<sheetData><row r=\"1\"><c r=\"XFE1\" t=\"inlineStr\"><is><t>too wide</t></is></c></row></sheetData>",
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            XlsxImporter().import(bytes.inputStream())
        }
    }

    @Test
    fun rejectsWorkbookWithoutVisibleSheetOrRows() {
        val hiddenOnly = xlsxFixture(
            sheets = listOf(hiddenSheet("Hidden", emptyList())),
        )
        assertThrows(IllegalStateException::class.java) {
            XlsxImporter().import(hiddenOnly.inputStream())
        }

        val emptyVisible = xlsxFixture(
            sheets = listOf(visibleSheet("Data", emptyList())),
        )
        assertThrows(IllegalStateException::class.java) {
            XlsxImporter().import(emptyVisible.inputStream())
        }
    }

    private data class SheetSpec(
        val name: String,
        val xml: String,
        val hidden: Boolean,
        val relationshipType: String = WORKSHEET_RELATIONSHIP_TYPE,
    )

    private fun hiddenSheet(name: String, rows: List<List<CellSpec>>): SheetSpec =
        SheetSpec(name, sheetXml(rows), hidden = true)

    private fun visibleSheet(name: String, rows: List<List<CellSpec>>): SheetSpec =
        SheetSpec(name, sheetXml(rows), hidden = false)

    private fun strictVisibleSheet(name: String, rows: List<List<CellSpec>>): SheetSpec =
        SheetSpec(
            name = name,
            xml = sheetXml(rows),
            hidden = false,
            relationshipType = STRICT_WORKSHEET_RELATIONSHIP_TYPE,
        )

    private fun nonWorksheetSheet(name: String, relationshipType: String): SheetSpec =
        SheetSpec(
            name = name,
            xml = "<chartsheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>",
            hidden = false,
            relationshipType = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/$relationshipType",
        )

    private fun visibleSheetXml(name: String, sheetData: String): SheetSpec =
        SheetSpec(
            name,
            """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              $sheetData
            </worksheet>
            """.trimIndent(),
            hidden = false,
        )

    private fun sheetXml(rows: List<List<CellSpec>>): String {
        val rowsXml = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { columnIndex, cell ->
                cell.toXml(columnName(columnIndex) + (rowIndex + 1))
            }.joinToString("")
            "<row r=\"${rowIndex + 1}\">$cells</row>"
        }.joinToString("")
        return """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>$rowsXml</sheetData>
            </worksheet>
        """.trimIndent()
    }

    private fun xlsxFixture(
        sheets: List<SheetSpec>,
        styles: String = defaultStyles(),
        date1904: Boolean = false,
        workbookXmlOverride: String? = null,
    ): ByteArray {
        val sharedValues = mutableListOf<String>()
        val transformedSheets = sheets.map { sheet ->
            var xml = sheet.xml
            val textPattern = Regex("<c([^>]*) data-shared=\"true\"([^>]*)><v>(.*?)</v></c>")
            xml = textPattern.replace(xml) { match ->
                val value = match.groupValues[3]
                val index = sharedValues.indexOf(value).let {
                    if (it >= 0) it else sharedValues.size.also { sharedValues += value }
                }
                "<c${match.groupValues[1]}${match.groupValues[2]} t=\"s\"><v>$index</v></c>"
            }
            sheet.copy(xml = xml)
        }
        val entries = linkedMapOf(
            "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            "xl/workbook.xml" to (workbookXmlOverride ?: workbookXml(transformedSheets, date1904)),
            "xl/_rels/workbook.xml.rels" to relationshipsXml(transformedSheets),
            "xl/styles.xml" to styles,
        )
        if (sharedValues.isNotEmpty()) {
            entries["xl/sharedStrings.xml"] = sharedStringsXml(sharedValues)
        }
        transformedSheets.forEachIndexed { index, sheet ->
            entries["xl/worksheets/sheet${index + 1}.xml"] = sheet.xml
        }
        return zipOf(*entries.entries.map { it.key to it.value }.toTypedArray())
    }

    private fun workbookXml(sheets: List<SheetSpec>, date1904: Boolean): String {
        val sheetXml = sheets.mapIndexed { index, sheet ->
            val state = if (sheet.hidden) " state=\"hidden\"" else ""
            "<sheet name=\"${sheet.name}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"$state/>"
        }.joinToString("")
        return """
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              ${if (date1904) "<workbookPr date1904=\"1\"/>" else ""}
              <sheets>$sheetXml</sheets>
            </workbook>
        """.trimIndent()
    }

    private fun relationshipsXml(sheets: List<SheetSpec>): String {
        val relationships = sheets.mapIndexed { index, sheet ->
            "<Relationship Id=\"rId${index + 1}\" Type=\"${sheet.relationshipType}\" Target=\"worksheets/sheet${index + 1}.xml\"/>"
        }.joinToString("")
        return """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$relationships</Relationships>
        """.trimIndent()
    }

    private fun minimalWorkbookXml(): String =
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Data\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private fun minimalRelationshipsXml(target: String = "worksheets/sheet1.xml"): String =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$WORKSHEET_RELATIONSHIP_TYPE\" Target=\"$target\"/></Relationships>"

    private fun sharedStringsXml(values: List<String>): String =
        "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">${values.joinToString("") { "<si><t>${escapeXml(it)}</t></si>" }}</sst>"

    private fun defaultStyles(): String =
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cellXfs count=\"1\"><xf numFmtId=\"0\"/></cellXfs></styleSheet>"

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        return zipOfBytes(*entries.map { (name, content) -> name to content.toByteArray() }.toTypedArray())
    }

    private fun zipOfBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private sealed interface CellSpec {
        fun toXml(reference: String): String
    }

    private data class SharedText(val value: String) : CellSpec {
        override fun toXml(reference: String): String =
            "<c r=\"$reference\" data-shared=\"true\"><v>${escapeXml(value)}</v></c>"
    }

    private data class InlineText(val value: String) : CellSpec {
        override fun toXml(reference: String): String =
            "<c r=\"$reference\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>"
    }

    private data class NumberValue(val value: String) : CellSpec {
        override fun toXml(reference: String): String =
            "<c r=\"$reference\"><v>${escapeXml(value)}</v></c>"
    }

    private fun text(value: String): CellSpec = SharedText(value)
    private fun inline(value: String): CellSpec = InlineText(value)
    private fun number(value: String): CellSpec = NumberValue(value)

    private fun columnName(index: Int): String {
        var value = index + 1
        val result = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            result.append(('A'.code + remainder).toChar())
            value = (value - 1) / 26
        }
        return result.reverse().toString()
    }

}

private const val WORKSHEET_RELATIONSHIP_TYPE =
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
private const val STRICT_WORKSHEET_RELATIONSHIP_TYPE =
    "http://purl.oclc.org/ooxml/officeDocument/relationships/worksheet"

private fun escapeXml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
