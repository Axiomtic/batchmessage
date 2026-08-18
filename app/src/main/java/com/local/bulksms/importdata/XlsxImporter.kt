package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * Reads the first visible worksheet from an OOXML workbook without a runtime library dependency.
 *
 * The importer deliberately returns at most 101 raw rows. The extra row is needed because the
 * first row may be a header; [HeaderDetector] makes the final 100-data-row decision once the user
 * has selected the header mode.
 */
class XlsxImporter : TableImporter {
    override fun import(input: InputStream): RawTable {
        val entries = unzipEntries(input)
        val workbook = parseWorkbook(requiredEntry(entries, WORKBOOK_PATH))
        val relationships = parseRelationships(requiredEntry(entries, WORKBOOK_RELATIONSHIPS_PATH))
        val sheet = workbook.sheets.firstOrNull {
            !it.hidden && relationships[it.relationshipId]?.type in WORKSHEET_RELATIONSHIP_TYPES
        }
            ?: error("工作簿没有可见工作表")
        val sheetPath = resolveSheetPath(relationships.getValue(sheet.relationshipId).target)
        val sheetXml = entries[sheetPath]
            ?: throw IllegalArgumentException("工作表关系指向不存在的条目: $sheetPath")
        val sharedStrings = entries[SHARED_STRINGS_PATH]
            ?.let(::parseSharedStrings)
            .orEmpty()
        val dateStyles = entries[STYLES_PATH]
            ?.let(::parseDateStyleIndexes)
            .orEmpty()
        return parseSheet(sheetXml, sharedStrings, dateStyles, workbook.date1904)
    }

    private fun unzipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = validateZipEntryPath(entry.name)
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (entries.containsKey(path)) {
                    throw IllegalArgumentException("XLSX 包含重复条目: $path")
                }
                if (entry.size > MAX_UNCOMPRESSED_BYTES) {
                    throw IllegalArgumentException("XLSX 解压数据超过 8 MiB 限制")
                }

                val output = ByteArrayOutputStream()
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    totalBytes += read.toLong()
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw IllegalArgumentException("XLSX 解压数据超过 8 MiB 限制")
                    }
                    output.write(buffer, 0, read)
                }
                entries[path] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun validateZipEntryPath(name: String): String {
        if (name.isBlank() || name.indexOf('\u0000') >= 0) {
            throw IllegalArgumentException("XLSX 包含无效条目路径")
        }
        if (name.startsWith('/') || name.startsWith('\\') || DRIVE_PATH.matches(name)) {
            throw IllegalArgumentException("XLSX 条目路径必须是相对路径: $name")
        }
        if (name.indexOf('\\') >= 0) {
            throw IllegalArgumentException("XLSX 条目路径不得包含反斜杠: $name")
        }

        val pathWithoutTrailingSlash = name.removeSuffix("/")
        val parts = pathWithoutTrailingSlash.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw IllegalArgumentException("XLSX 条目路径包含非法分段: $name")
        }
        return name
    }

    private fun parseWorkbook(bytes: ByteArray): WorkbookInfo {
        val document = parseXml(bytes, WORKBOOK_PATH)
        val sheets = descendantElements(document, "sheet").map { sheet ->
            val relationshipId = sheet.attribute("r:id")
                .ifBlank { sheet.attribute("id") }
                .ifBlank { throw IllegalArgumentException("工作表缺少关系 ID") }
            WorkbookSheet(
                relationshipId = relationshipId,
                hidden = sheet.attribute("state").lowercase(Locale.US) in HIDDEN_STATES,
            )
        }
        val date1904 = descendantElements(document, "workbookPr").firstOrNull()
            ?.attribute("date1904")
            ?.lowercase(Locale.US) in setOf("1", "true")
        return WorkbookInfo(sheets = sheets, date1904 = date1904)
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, WorkbookRelationship> {
        val document = parseXml(bytes, WORKBOOK_RELATIONSHIPS_PATH)
        return descendantElements(document, "Relationship").associate { relationship ->
            val id = relationship.attribute("Id")
                .ifBlank { throw IllegalArgumentException("工作表关系缺少 ID") }
            val type = relationship.attribute("Type")
            val target = relationship.attribute("Target")
                .ifBlank { throw IllegalArgumentException("工作表关系缺少目标路径: $id") }
            id to WorkbookRelationship(type = type, target = target)
        }
    }

    private fun resolveSheetPath(target: String): String {
        if (target.startsWith('/') || target.startsWith('\\') || DRIVE_PATH.matches(target)) {
            throw IllegalArgumentException("工作表关系路径必须是相对路径: $target")
        }
        if (target.indexOf('\\') >= 0 || target.split('/').any { it == ".." || it == "." }) {
            throw IllegalArgumentException("工作表关系路径包含非法分段: $target")
        }
        val path = target.removePrefix("/").let { targetPath ->
            if (targetPath.startsWith("xl/")) targetPath else "xl/$targetPath"
        }
        return validateZipEntryPath(path)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes, SHARED_STRINGS_PATH)
        return descendantElements(document, "si").map { stringItem ->
            descendantElements(stringItem, "t").joinToString(separator = "") { it.textContent }
        }
    }

    private fun parseDateStyleIndexes(bytes: ByteArray): Map<Int, DateStyle> {
        val document = parseXml(bytes, STYLES_PATH)
        val customFormats = descendantElements(document, "numFmt").mapNotNull { format ->
            val id = format.attribute("numFmtId").toIntOrNull() ?: return@mapNotNull null
            id to format.attribute("formatCode")
        }.toMap()
        val cellXfs = descendantElements(document, "cellXfs").firstOrNull() ?: return emptyMap()
        return childElements(cellXfs, "xf").mapIndexedNotNull { styleIndex, xf ->
            val numFmtId = xf.attribute("numFmtId").toIntOrNull() ?: return@mapIndexedNotNull null
            val formatCode = customFormats[numFmtId] ?: builtInDateFormat(numFmtId)
                ?: return@mapIndexedNotNull null
            if (!isDateFormat(formatCode)) return@mapIndexedNotNull null
            styleIndex to DateStyle(hasTime = hasTimePart(formatCode))
        }.toMap()
    }

    private fun parseSheet(
        bytes: ByteArray,
        sharedStrings: List<String>,
        dateStyles: Map<Int, DateStyle>,
        date1904: Boolean,
    ): RawTable {
        val document = parseXml(bytes, "工作表")
        val rowElements = descendantElements(document, "row")
        if (rowElements.isEmpty()) {
            error("工作表为空")
        }

        val rows = mutableListOf<List<String>>()
        val warnings = mutableListOf<String>()
        for (rowElement in rowElements) {
            val nextRowNumber = rows.size + 1
            if (nextRowNumber > MAX_RAW_ROWS) {
                throw ImportLimitExceeded(nextRowNumber)
            }
            val cells = mutableMapOf<Int, String>()
            var implicitColumn = 0
            for (cell in childElements(rowElement, "c")) {
                val reference = cell.attribute("r")
                val columnIndex = columnIndex(reference) ?: run {
                    if (implicitColumn >= MAX_COLUMN_COUNT) {
                        throw IllegalArgumentException("单元格列超出 Excel 限制: $reference")
                    }
                    while (cells.containsKey(implicitColumn)) implicitColumn++
                    implicitColumn
                }
                implicitColumn = maxOf(implicitColumn + 1, columnIndex + 1)
                cells[columnIndex] = parseCell(cell, sharedStrings, dateStyles, warnings, date1904)
            }
            val width = cells.keys.maxOrNull()?.plus(1) ?: 0
            rows += List(width) { index -> cells[index].orEmpty() }
        }

        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width == 0 || rows.all { row -> row.all(String::isEmpty) }) {
            error("工作表为空")
        }
        val normalizedRows = rows.map { row ->
            if (row.size >= width) row else row + List(width - row.size) { "" }
        }
        return RawTable(rows = normalizedRows, warnings = warnings)
    }

    private fun parseCell(
        cell: Element,
        sharedStrings: List<String>,
        dateStyles: Map<Int, DateStyle>,
        warnings: MutableList<String>,
        date1904: Boolean,
    ): String {
        val reference = cell.attribute("r").ifBlank { "未知单元格" }
        val type = cell.attribute("t")
        val valueElement = childElements(cell, "v").firstOrNull()
        val rawValue = valueElement?.textContent.orEmpty()
        val trimmedValue = rawValue.trim()
        val formula = childElements(cell, "f").isNotEmpty()
        val inlineElement = childElements(cell, "is").firstOrNull()
        if (formula && (valueElement == null || trimmedValue.isEmpty())) {
            warnings += "公式单元格 $reference 没有缓存值，已留空"
            return ""
        }

        if (type == "inlineStr") {
            return inlineElement?.let(::richTextValue).orEmpty()
        }
        if (type == "s") {
            val index = trimmedValue.toIntOrNull()
            return if (index != null && index in sharedStrings.indices) {
                sharedStrings[index]
            } else {
                warnings += "单元格 $reference 的共享字符串索引无效，已留空"
                ""
            }
        }
        if (type == "b") {
            return when (trimmedValue.lowercase(Locale.US)) {
                "1", "true" -> "TRUE"
                "0", "false" -> "FALSE"
                else -> rawValue
            }
        }
        val style = cell.attribute("s").toIntOrNull()?.let(dateStyles::get)
        if (style != null && trimmedValue.isNotBlank() && (type.isBlank() || type == "n" || formula)) {
            ExcelDateFormat.format(trimmedValue, style.hasTime, date1904)?.let { return it }
        }
        return if (type.isBlank() || type == "n" || formula) trimmedValue else rawValue
    }

    private fun richTextValue(element: Element): String =
        descendantElements(element, "t").joinToString(separator = "") { it.textContent }

    private fun parseXml(bytes: ByteArray, description: String): Document {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isExpandEntityReferences = false
            factory.setFeature(SECURE_PROCESSING_FEATURE, true)
            factory.setFeature(DISALLOW_DOCTYPE_FEATURE, true)
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
            factory.setFeature(LOAD_EXTERNAL_DTD_FEATURE, false)
            factory.setAttribute(ACCESS_EXTERNAL_DTD_PROPERTY, "")
            factory.setAttribute(ACCESS_EXTERNAL_SCHEMA_PROPERTY, "")
            factory.isXIncludeAware = false
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver(org.xml.sax.EntityResolver { _, _ ->
                InputSource(StringReader(""))
            })
            return builder.parse(ByteArrayInputStream(bytes))
        } catch (exception: Exception) {
            throw IllegalArgumentException("无法解析 $description", exception)
        }
    }

    private fun descendantElements(node: Node, name: String): List<Element> {
        val withNamespace = (node as? Document)?.getElementsByTagNameNS("*", name)
            ?: (node as? Element)?.getElementsByTagNameNS("*", name)
        if (withNamespace != null && withNamespace.length > 0) {
            return (0 until withNamespace.length).mapNotNull { withNamespace.item(it) as? Element }
        }
        val withoutNamespace = when (node) {
            is Document -> node.getElementsByTagName(name)
            is Element -> node.getElementsByTagName(name)
            else -> null
        } ?: return emptyList()
        return (0 until withoutNamespace.length).mapNotNull { withoutNamespace.item(it) as? Element }
    }

    private fun childElements(parent: Element, name: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && (child.localName == name || child.tagName == name)) {
                result += child
            }
            child = child.nextSibling
        }
        return result
    }

    private fun Element.attribute(name: String): String {
        val direct = getAttribute(name).orEmpty()
        if (direct.isNotBlank() || !name.contains(':')) return direct
        return getAttributeNS(RELATIONSHIPS_NS, name.substringAfter(':')).orEmpty()
    }

    private fun columnIndex(reference: String): Int? {
        if (reference.isBlank()) return null
        val letters = reference.trim('$').takeWhile { it in 'A'..'Z' || it in 'a'..'z' }
        if (letters.isEmpty()) return null
        var index = 0L
        for (letter in letters) {
            index = index * 26 + (letter.uppercaseChar() - 'A' + 1)
            if (index > MAX_COLUMN_COUNT) {
                throw IllegalArgumentException("单元格列超出 Excel 限制: $reference")
            }
        }
        return (index - 1).toInt()
    }

    private fun isDateFormat(formatCode: String): Boolean = ExcelDateFormat.isDateFormat(formatCode)

    private fun hasTimePart(formatCode: String): Boolean = ExcelDateFormat.hasTimePart(formatCode)

    private fun builtInDateFormat(numFmtId: Int): String? = ExcelDateFormat.builtInDateFormat(numFmtId)

    private data class WorkbookSheet(
        val relationshipId: String,
        val hidden: Boolean,
    )

    private data class WorkbookInfo(
        val sheets: List<WorkbookSheet>,
        val date1904: Boolean,
    )

    private data class WorkbookRelationship(
        val type: String,
        val target: String,
    )

    private data class DateStyle(val hasTime: Boolean)

    private fun requiredEntry(entries: Map<String, ByteArray>, path: String): ByteArray =
        entries[path] ?: throw IllegalArgumentException("XLSX 缺少必需条目: $path")

    private companion object {
        const val WORKBOOK_PATH = "xl/workbook.xml"
        const val WORKBOOK_RELATIONSHIPS_PATH = "xl/_rels/workbook.xml.rels"
        const val SHARED_STRINGS_PATH = "xl/sharedStrings.xml"
        const val STYLES_PATH = "xl/styles.xml"
        const val MAX_UNCOMPRESSED_BYTES = 8L * 1024L * 1024L
        const val MAX_COLUMN_COUNT = 16_384
        const val MAX_RAW_ROWS = HeaderDetector.MAX_DATA_ROWS + 1
        const val BUFFER_SIZE = 8 * 1024
        const val WORKSHEET_RELATIONSHIP_TYPE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
        const val STRICT_WORKSHEET_RELATIONSHIP_TYPE =
            "http://purl.oclc.org/ooxml/officeDocument/relationships/worksheet"
        val WORKSHEET_RELATIONSHIP_TYPES = setOf(
            WORKSHEET_RELATIONSHIP_TYPE,
            STRICT_WORKSHEET_RELATIONSHIP_TYPE,
        )
        val HIDDEN_STATES = setOf("hidden", "veryhidden")
        val DRIVE_PATH = Regex("^[A-Za-z]:.*")
        const val RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val SECURE_PROCESSING_FEATURE = "http://javax.xml.XMLConstants/feature/secure-processing"
        const val DISALLOW_DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"
        const val EXTERNAL_GENERAL_ENTITIES_FEATURE = "http://xml.org/sax/features/external-general-entities"
        const val EXTERNAL_PARAMETER_ENTITIES_FEATURE = "http://xml.org/sax/features/external-parameter-entities"
        const val LOAD_EXTERNAL_DTD_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        const val ACCESS_EXTERNAL_DTD_PROPERTY = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA_PROPERTY = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    }
}
