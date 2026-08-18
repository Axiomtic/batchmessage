package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable
import java.io.InputStream
import java.math.BigDecimal
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Reads the first visible worksheet from a legacy BIFF8 (.xls) workbook without any
 * runtime library dependency.
 *
 * The importer handles the OLE2 compound document container and the BIFF8 record
 * stream: shared strings, number / RK / boolean / error / formula cells and
 * date-formatted numbers.
 */
class XlsImporter : TableImporter {
    override fun import(input: InputStream): RawTable {
        val bytes = input.readBytes()
        val compound = Ole2CompoundDocument.parse(bytes)
        val workbook = compound.stream("Workbook") ?: compound.stream("Book")
            ?: throw IllegalArgumentException("XLS 文件中未找到工作簿流")
        return Biff8Parser(workbook).parse()
    }
}

/**
 * Minimal OLE2 compound document reader: header, DIFAT/FAT chains, the directory
 * tree, the mini stream and the mini FAT. Only what the BIFF8 reader needs.
 */
internal class Ole2CompoundDocument private constructor(
    private val bytes: ByteArray,
    private val sectorSize: Int,
    private val fat: IntArray,
    private val miniFat: IntArray,
    private val miniStream: ByteArray,
    private val directoryEntries: List<DirectoryEntry>,
) {
    data class DirectoryEntry(
        val name: String,
        val isStream: Boolean,
        val startSector: Int,
        val streamSize: Long,
    )

    /** Returns the bytes of the named stream (case-insensitive), or null. */
    fun stream(name: String): ByteArray? {
        val entry = directoryEntries.firstOrNull {
            it.isStream && it.name.equals(name, ignoreCase = true)
        } ?: return null
        return if (entry.streamSize < MINI_STREAM_CUTOFF) {
            readMiniChain(entry.startSector, entry.streamSize.toInt())
        } else {
            readSectorChain(entry.startSector, entry.streamSize.toInt())
        }
    }

    private fun readSectorChain(startSector: Int, size: Int): ByteArray {
        val result = ByteArray(size)
        var sector = startSector
        var written = 0
        var guard = 0
        while (sector >= 0 && written < size) {
            if (sector >= fat.size) throw IllegalArgumentException("XLS 扇区链越界")
            val offset = sector * sectorSize
            val count = minOf(sectorSize, size - written)
            System.arraycopy(bytes, offset, result, written, count)
            written += count
            sector = fat[sector]
            if (++guard > fat.size + 2) throw IllegalArgumentException("XLS 扇区链存在循环")
        }
        if (written < size) throw IllegalArgumentException("XLS 流不完整")
        return result
    }

    private fun readMiniChain(startSector: Int, size: Int): ByteArray {
        val result = ByteArray(size)
        var sector = startSector
        var written = 0
        var guard = 0
        while (sector >= 0 && written < size) {
            if (sector >= miniFat.size) throw IllegalArgumentException("XLS 迷你扇区链越界")
            val offset = sector * MINI_SECTOR_SIZE
            val count = minOf(MINI_SECTOR_SIZE, size - written)
            System.arraycopy(miniStream, offset, result, written, count)
            written += count
            sector = miniFat[sector]
            if (++guard > miniFat.size + 2) throw IllegalArgumentException("XLS 迷你扇区链存在循环")
        }
        if (written < size) throw IllegalArgumentException("XLS 迷你流不完整")
        return result
    }

    companion object {
        private const val MINI_SECTOR_SIZE = 64
        private const val MINI_STREAM_CUTOFF = 4096L
        private const val END_OF_CHAIN = -2
        private const val FREE_SECTOR = -1

        private val SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        )

        fun parse(bytes: ByteArray): Ole2CompoundDocument {
            if (bytes.size < 512 || !SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }) {
                throw IllegalArgumentException("无法识别的 Excel 文件格式，请使用 .xlsx 或 .xls 文件")
            }
            val reader = ByteReader(bytes, 0)
            reader.skip(0x1E)
            val sectorSize = 1 shl reader.u16()
            if (sectorSize < 512 || sectorSize > 4096) {
                throw IllegalArgumentException("XLS 扇区大小异常: $sectorSize")
            }
            reader.skip(2) // mini sector shift, always 6
            reader.skip(6) // reserved
            reader.skip(4) // number of directory sectors
            val fatSectorCount = reader.u32()
            val firstDirectorySector = reader.u32()
            reader.skip(4) // transaction signature
            val miniStreamCutoff = reader.u32().toLong()
            val firstMiniFatSector = reader.u32()
            val miniFatSectorCount = reader.u32()
            val firstDifatSector = reader.u32()
            val difatSectorCount = reader.u32()

            // DIFAT: 109 entries in the header, then chained DIFAT sectors.
            val difat = mutableListOf<Int>()
            repeat(109) { difat += reader.i32() }
            var difatSector = firstDifatSector
            repeat(difatSectorCount) {
                if (difatSector < 0) throw IllegalArgumentException("XLS DIFAT 链不完整")
                val sectorBytes = readRawSector(bytes, sectorSize, difatSector)
                val sectorReader = ByteReader(sectorBytes, 0)
                repeat(sectorSize / 4 - 1) {
                    val value = sectorReader.i32()
                    if (value >= 0) difat += value
                }
                difatSector = sectorReader.i32()
            }

            // FAT: concatenate every FAT sector referenced by the DIFAT.
            val fatEntries = ArrayList<Int>(difat.size * (sectorSize / 4))
            for (fatSector in difat) {
                if (fatSector < 0) continue // FREESECT / ENDOFCHAIN markers
                val sectorBytes = readRawSector(bytes, sectorSize, fatSector)
                val sectorReader = ByteReader(sectorBytes, 0)
                repeat(sectorSize / 4) { fatEntries += sectorReader.i32() }
            }
            val fat = IntArray(fatEntries.size) { fatEntries[it] }
            if (firstDirectorySector < 0 || firstDirectorySector >= fat.size) {
                throw IllegalArgumentException("XLS 目录扇区无效")
            }

            // Directory stream and its 128-byte entries.
            val directoryBytes = readChain(
                bytes = bytes,
                fat = fat,
                sectorSize = sectorSize,
                startSector = firstDirectorySector,
                size = chainSize(fat, firstDirectorySector) * sectorSize,
            )
            val entries = mutableListOf<DirectoryEntry>()
            var position = 0
            while (position + 128 <= directoryBytes.size) {
                val nameBytes = directoryBytes.copyOfRange(position, position + 64)
                val nameLength = (directoryBytes[position + 64].toInt() and 0xFF) or
                    ((directoryBytes[position + 65].toInt() and 0xFF) shl 8)
                val objectType = directoryBytes[position + 66].toInt() and 0xFF
                val startSector = i32At(directoryBytes, position + 116)
                val streamSize = i64At(directoryBytes, position + 120)
                val name = if (nameLength >= 2) {
                    String(nameBytes, 0, nameLength - 2, StandardCharsets.UTF_16LE)
                } else {
                    ""
                }
                if (objectType == 2) { // stream
                    entries += DirectoryEntry(
                        name = name,
                        isStream = true,
                        startSector = startSector,
                        streamSize = streamSize,
                    )
                } else if (objectType == 5) { // root storage
                    entries += DirectoryEntry(
                        name = "Root Entry",
                        isStream = false,
                        startSector = startSector,
                        streamSize = streamSize,
                    )
                }
                position += 128
            }

            val root = entries.firstOrNull { !it.isStream }
                ?: throw IllegalArgumentException("XLS 缺少根目录项")
            val miniStream = if (root.streamSize > 0L && root.startSector >= 0) {
                readChain(
                    bytes = bytes,
                    fat = fat,
                    sectorSize = sectorSize,
                    startSector = root.startSector,
                    size = root.streamSize.toInt(),
                )
            } else {
                ByteArray(0)
            }
            val miniFatEntries = ArrayList<Int>(miniFatSectorCount * (sectorSize / 4))
            var miniFatSector = firstMiniFatSector
            repeat(miniFatSectorCount) {
                if (miniFatSector < 0) throw IllegalArgumentException("XLS 迷你 FAT 链不完整")
                val sectorBytes = readRawSector(bytes, sectorSize, miniFatSector)
                val sectorReader = ByteReader(sectorBytes, 0)
                repeat(sectorSize / 4) { miniFatEntries += sectorReader.i32() }
                miniFatSector = fat.getOrElse(miniFatSector) { END_OF_CHAIN }
            }
            val miniFat = IntArray(miniFatEntries.size) { miniFatEntries[it] }

            return Ole2CompoundDocument(
                bytes = bytes,
                sectorSize = sectorSize,
                fat = fat,
                miniFat = miniFat,
                miniStream = miniStream,
                directoryEntries = entries.filter { it.isStream },
            )
        }

        private fun chainSize(fat: IntArray, startSector: Int): Int {
            var count = 0
            var sector = startSector
            var guard = 0
            while (sector >= 0) {
                count++
                sector = fat.getOrElse(sector) { END_OF_CHAIN }
                if (++guard > fat.size + 2) throw IllegalArgumentException("XLS 目录扇区链存在循环")
            }
            return count
        }

        private fun readRawSector(bytes: ByteArray, sectorSize: Int, sector: Int): ByteArray {
            val offset = sector * sectorSize
            if (offset < 0 || offset + sectorSize > bytes.size) {
                throw IllegalArgumentException("XLS 扇区越界: $sector")
            }
            return bytes.copyOfRange(offset, offset + sectorSize)
        }

        private fun readChain(
            bytes: ByteArray,
            fat: IntArray,
            sectorSize: Int,
            startSector: Int,
            size: Int,
        ): ByteArray {
            val result = ByteArray(size)
            var sector = startSector
            var written = 0
            var guard = 0
            while (sector >= 0 && written < size) {
                val offset = sector * sectorSize
                val count = minOf(sectorSize, size - written)
                System.arraycopy(bytes, offset, result, written, count)
                written += count
                sector = fat.getOrElse(sector) { END_OF_CHAIN }
                if (++guard > fat.size + 2) throw IllegalArgumentException("XLS 扇区链存在循环")
            }
            return result
        }

        private fun i32At(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        private fun i64At(bytes: ByteArray, offset: Int): Long {
            val low = i32At(bytes, offset).toLong() and 0xFFFFFFFFL
            val high = i32At(bytes, offset + 4).toLong() and 0xFFFFFFFFL
            return low or (high shl 32)
        }
    }
}

/**
 * BIFF8 record-stream reader. The first substream holds workbook globals (code page,
 * shared strings, number formats, XF records, sheet bounds); the chosen visible
 * worksheet substream holds the cell records.
 */
internal class Biff8Parser(
    private val stream: ByteArray,
) {
    private var position = 0
    private val sharedStrings = mutableListOf<String>()
    private val customFormats = mutableMapOf<Int, String>()
    private val xfFormatIds = mutableListOf<Int>()
    private val sheetOffsets = mutableListOf<SheetBounds>()
    private var date1904 = false
    private var codePage = 0x04B0

    /** Record-boundary cursor used while reading SST strings across CONTINUE records. */
    private var recordEnd = 0
    private var readCursor = 0

    private data class SheetBounds(val offset: Int, val hidden: Boolean)

    fun parse(): RawTable {
        // --- Workbook globals substream ---
        while (position + 4 <= stream.size) {
            val type = u16(position)
            val length = u16(position + 2)
            val data = position + 4
            if (type == RECORD_BOF) {
                val substream = u16(data)
                position += 4 + length
                if (substream == 0x0010) break // worksheet BOF: cells follow
                continue
            }
            when (type) {
                RECORD_CODEPAGE -> codePage = u16(data)
                RECORD_DATEMODE -> date1904 = u16(data) == 1
                RECORD_SST -> {
                    parseSst(data, length)
                    continue
                }
                RECORD_FORMAT -> parseFormat(data, length)
                RECORD_XF -> parseXf(data, length)
                RECORD_BOUNDSHEET -> parseBoundsheet(data, length)
            }
            position += 4 + length
        }

        // Jump to the first visible worksheet, falling back to the current position.
        val target = sheetOffsets.firstOrNull { !it.hidden }?.offset
            ?: sheetOffsets.firstOrNull()?.offset
        if (target != null && target in 0 until stream.size) {
            position = target
        }
        return parseWorksheet()
    }

    private fun parseWorksheet(): RawTable {
        val cells = mutableMapOf<Int, MutableMap<Int, String>>()
        val warnings = mutableListOf<String>()
        var pendingFormula: Pair<Int, Int>? = null

        while (position + 4 <= stream.size) {
            val type = u16(position)
            val length = u16(position + 2)
            val data = position + 4
            if (type == RECORD_EOF) break
            when (type) {
                RECORD_LABEL_SST -> {
                    val row = u16(data)
                    val column = u16(data + 2)
                    val sstIndex = i32(data + 6)
                    val value = sharedStrings.getOrNull(sstIndex)
                        ?: run {
                            warnings += "单元格 (${row + 1},${column + 1}) 的共享字符串索引无效，已留空"
                            ""
                        }
                    cells.getOrPut(row) { mutableMapOf() }[column] = value
                }
                RECORD_NUMBER -> {
                    val row = u16(data)
                    val column = u16(data + 2)
                    val xf = u16(data + 4)
                    val value = doubleAt(data + 6)
                    cells.getOrPut(row) { mutableMapOf() }[column] = formatNumber(value, xf)
                }
                RECORD_RK -> {
                    val row = u16(data)
                    val column = u16(data + 2)
                    val xf = u16(data + 4)
                    val value = decodeRk(i32(data + 6))
                    cells.getOrPut(row) { mutableMapOf() }[column] = formatNumber(value, xf)
                }
                RECORD_MUL_RK -> {
                    val row = u16(data)
                    val columnFirst = u16(data + 2)
                    val columnLast = u16(data + length - 2)
                    var column = columnFirst
                    var cursor = data + 4
                    while (column <= columnLast && cursor + 6 <= data + length - 2) {
                        val xf = u16(cursor)
                        val value = decodeRk(i32(cursor + 2))
                        cells.getOrPut(row) { mutableMapOf() }[column] = formatNumber(value, xf)
                        cursor += 6
                        column++
                    }
                }
                RECORD_BOOL_ERR -> {
                    val row = u16(data)
                    val column = u16(data + 2)
                    val isError = stream[data + 6].toInt() and 0xFF
                    val value = stream[data + 5].toInt() and 0xFF
                    val text = if (isError != 0) biffErrorText(value) else if (value != 0) "TRUE" else "FALSE"
                    cells.getOrPut(row) { mutableMapOf() }[column] = text
                }
                RECORD_FORMULA -> {
                    val row = u16(data)
                    val column = u16(data + 2)
                    val xf = u16(data + 4)
                    val result = formulaResult(doubleAt(data + 6))
                    when (result.type) {
                        FormulaValueType.DOUBLE ->
                            cells.getOrPut(row) { mutableMapOf() }[column] = formatNumber(result.value, xf)
                        FormulaValueType.BOOLEAN ->
                            cells.getOrPut(row) { mutableMapOf() }[column] =
                                if (result.boolean) "TRUE" else "FALSE"
                        FormulaValueType.ERROR ->
                            cells.getOrPut(row) { mutableMapOf() }[column] = biffErrorText(result.errorCode)
                        FormulaValueType.EMPTY ->
                            cells.getOrPut(row) { mutableMapOf() }[column] = ""
                        FormulaValueType.STRING -> pendingFormula = row to column
                    }
                }
                RECORD_STRING -> {
                    pendingFormula?.let { (row, column) ->
                        cells.getOrPut(row) { mutableMapOf() }[column] = readUnicodeString(data, length)
                        pendingFormula = null
                    }
                }
            }
            position += 4 + length
        }

        if (cells.isEmpty()) {
            error("工作表为空")
        }
        val width = (cells.values.maxOfOrNull { it.keys.maxOrNull() ?: -1 } ?: -1) + 1
        val rowCount = (cells.keys.maxOrNull() ?: -1) + 1
        val rows = List(rowCount) { rowIndex ->
            val rowCells = cells[rowIndex].orEmpty()
            List(width) { columnIndex -> rowCells[columnIndex].orEmpty() }
        }
        if (width == 0 || rows.all { row -> row.all(String::isEmpty) }) {
            error("工作表为空")
        }
        return RawTable(rows = rows, warnings = warnings)
    }

    // --- globals record parsers ---

    private fun parseFormat(data: Int, length: Int) {
        if (length < 4) return
        val formatId = u16(data)
        val value = readFormatString(data + 2, length - 2)
        customFormats[formatId] = value
    }

    /** FORMAT records carry an XLUnicodeString with a 2-byte character count. */
    private fun readFormatString(data: Int, length: Int): String {
        if (length < 3) return ""
        val cch = u16(data)
        val flags = stream[data + 2].toInt() and 0xFF
        val compressed = flags and 0x08 != 0
        val charBytes = data + 3
        val expected = if (compressed) cch else cch * 2
        if (charBytes + expected > data + length) return ""
        return if (compressed) {
            String(stream, charBytes, cch, codePageCharset())
        } else {
            String(stream, charBytes, cch * 2, StandardCharsets.UTF_16LE)
        }
    }

    private fun parseXf(data: Int, length: Int) {
        // XF: fontId(2) + format(2) + protection(2) + ...; only the format id matters.
        if (length >= 6) {
            xfFormatIds += u16(data + 2)
        }
    }

    private fun parseBoundsheet(data: Int, length: Int) {
        if (length < 6) return
        val offset = i32(data)
        val state = stream[data + 4].toInt() and 0xFF
        sheetOffsets += SheetBounds(
            offset = offset,
            hidden = state == 0x01 || state == 0x02,
        )
    }

    private fun parseSst(data: Int, length: Int) {
        // cstTotal(4) + cstUnique(4), then unique strings; a string longer than the
        // record continues into CONTINUE records whose first byte re-declares the
        // character encoding flag.
        recordEnd = data + length
        readCursor = data + 8
        val uniqueCount = i32(data + 4)
        repeat(uniqueCount) {
            if (readCursor >= recordEnd && !nextRecordIsContinue()) {
                return@repeat
            }
            sharedStrings += readSstString()
        }
        position = recordEnd
    }

    private fun readSstString(): String {
        val cch = readU16()
        val flags = readU8()
        var highCch = 0
        if (flags and 0x01 != 0) highCch = readU16()
        val totalCch = cch or (highCch shl 16)
        var compressed = flags and 0x08 != 0
        val rich = flags and 0x04 != 0
        val hasExt = flags and 0x02 != 0
        val cRun = if (rich) readU16() else 0
        val cbExtRst = if (rich && hasExt) readU32() else 0

        val builder = StringBuilder(totalCch)
        var remaining = totalCch
        while (remaining > 0) {
            if (readCursor >= recordEnd) {
                advanceToContinue()
                compressed = readU8() and 0x08 != 0
            }
            val count = minOf(remaining, recordEnd - readCursor)
            builder.append(readChars(count, compressed))
            remaining -= count
        }
        if (rich) skipBytes(cRun * 4L + cbExtRst.toLong())
        return builder.toString()
    }

    private fun nextRecordIsContinue(): Boolean =
        readCursor + 4 <= stream.size && u16(readCursor) == RECORD_CONTINUE

    private fun advanceToContinue() {
        position = recordEnd
        if (u16(position) != RECORD_CONTINUE) {
            throw IllegalArgumentException("XLS 共享字符串被意外截断")
        }
        val length = u16(position + 2)
        recordEnd = position + 4 + length
        readCursor = position + 4
    }

    private fun readU16(): Int {
        if (readCursor + 2 > recordEnd) advanceToContinue()
        val value = (stream[readCursor].toInt() and 0xFF) or
            ((stream[readCursor + 1].toInt() and 0xFF) shl 8)
        readCursor += 2
        return value
    }

    private fun readU32(): Int {
        if (readCursor + 4 > recordEnd) advanceToContinue()
        val value = (stream[readCursor].toInt() and 0xFF) or
            ((stream[readCursor + 1].toInt() and 0xFF) shl 8) or
            ((stream[readCursor + 2].toInt() and 0xFF) shl 16) or
            ((stream[readCursor + 3].toInt() and 0xFF) shl 24)
        readCursor += 4
        return value
    }

    private fun readU8(): Int {
        if (readCursor >= recordEnd) advanceToContinue()
        return stream[readCursor++].toInt() and 0xFF
    }

    private fun readChars(count: Int, compressed: Boolean): String {
        if (compressed) {
            val chunk = stream.copyOfRange(readCursor, readCursor + count)
            readCursor += count
            return String(chunk, codePageCharset())
        }
        val chunk = stream.copyOfRange(readCursor, readCursor + count * 2)
        readCursor += count * 2
        return String(chunk, StandardCharsets.UTF_16LE)
    }

    private fun skipBytes(count: Long) {
        var remaining = count
        while (remaining > 0) {
            if (readCursor >= recordEnd) advanceToContinue()
            val skip = minOf(remaining, (recordEnd - readCursor).toLong())
            readCursor += skip.toInt()
            remaining -= skip
        }
    }

    private fun readUnicodeString(data: Int, length: Int): String {
        // Short XLUnicodeString: cch(1) + flags(1) + chars.
        if (length < 2) return ""
        val cch = stream[data].toInt() and 0xFF
        val flags = stream[data + 1].toInt() and 0xFF
        val compressed = flags and 0x08 != 0
        val charBytes = data + 2
        val expected = if (compressed) cch else cch * 2
        if (charBytes + expected > data + length) return ""
        return if (compressed) {
            String(stream, charBytes, cch, codePageCharset())
        } else {
            String(stream, charBytes, cch * 2, StandardCharsets.UTF_16LE)
        }
    }

    private fun codePageCharset(): Charset = when (codePage) {
        0x04B0 -> StandardCharsets.UTF_16LE
        0x03A8, 0x04E4 -> Charset.forName("GBK")
        0x04E2, 0x04E3 -> Charset.forName("GB18030")
        else -> Charset.forName("windows-1252")
    }

    // --- cell value helpers ---

    private fun decodeRk(value: Int): Double {
        val is100x = value and 0x01 != 0
        val isDouble = value and 0x02 != 0
        return when {
            isDouble -> {
                val bits = (value.toLong() and 0xFFFFFFFCL) shl 34
                Double.fromBits(bits)
            }
            else -> {
                val integer = value shr 2
                if (is100x) integer / 100.0 else integer.toDouble()
            }
        }
    }

    private fun formatNumber(value: Double, xfIndex: Int): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val formatId = xfFormatIds.getOrNull(xfIndex)
        val formatCode = formatId?.let {
            customFormats[it] ?: ExcelDateFormat.builtInDateFormat(it)
        }
        if (formatId != null && formatCode != null && ExcelDateFormat.isDateFormat(formatCode)) {
            ExcelDateFormat.format(
                value.toString(),
                ExcelDateFormat.hasTimePart(formatCode),
                date1904,
            )?.let { return it }
        }
        val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
        return if (decimal.scale() < 0) {
            decimal.setScale(0).toPlainString()
        } else {
            decimal.toPlainString()
        }
    }

    private fun formulaResult(raw: Double): FormulaResult {
        // A non-numeric cached result has 0xFFFF in the low 2 bytes, the value type
        // in bytes 2-3 and the boolean/error payload in the high word.
        val bits = raw.toBits()
        val low = bits.toInt()
        if (low and 0xFFFF == 0xFFFF) {
            val type = (low ushr 16) and 0xFF
            val payload = ((bits shr 32) and 0xFFFF).toInt()
            return when (type) {
                0 -> FormulaResult(FormulaValueType.STRING)
                1 -> FormulaResult(FormulaValueType.BOOLEAN, boolean = payload != 0)
                2 -> FormulaResult(FormulaValueType.ERROR, errorCode = payload and 0xFF)
                3 -> FormulaResult(FormulaValueType.EMPTY)
                else -> FormulaResult(FormulaValueType.DOUBLE, value = raw)
            }
        }
        return FormulaResult(FormulaValueType.DOUBLE, value = raw)
    }

    private fun biffErrorText(code: Int): String = when (code) {
        0x00 -> "#NULL!"
        0x07 -> "#DIV/0!"
        0x0F -> "#VALUE!"
        0x17 -> "#REF!"
        0x1D -> "#NAME?"
        0x24 -> "#NUM!"
        0x2A -> "#N/A"
        else -> "#ERR($code)"
    }

    private enum class FormulaValueType { DOUBLE, STRING, BOOLEAN, ERROR, EMPTY }

    private data class FormulaResult(
        val type: FormulaValueType,
        val value: Double = 0.0,
        val boolean: Boolean = false,
        val errorCode: Int = 0,
    )

    // --- primitives over the record stream ---

    private fun u16(offset: Int): Int {
        require(offset >= 0 && offset + 2 <= stream.size) { "XLS 记录越界" }
        return (stream[offset].toInt() and 0xFF) or ((stream[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun i32(offset: Int): Int {
        require(offset >= 0 && offset + 4 <= stream.size) { "XLS 记录越界" }
        return (stream[offset].toInt() and 0xFF) or
            ((stream[offset + 1].toInt() and 0xFF) shl 8) or
            ((stream[offset + 2].toInt() and 0xFF) shl 16) or
            ((stream[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun doubleAt(offset: Int): Double {
        require(offset >= 0 && offset + 8 <= stream.size) { "XLS 记录越界" }
        val bits = (stream[offset].toLong() and 0xFF) or
            ((stream[offset + 1].toLong() and 0xFF) shl 8) or
            ((stream[offset + 2].toLong() and 0xFF) shl 16) or
            ((stream[offset + 3].toLong() and 0xFF) shl 24) or
            ((stream[offset + 4].toLong() and 0xFF) shl 32) or
            ((stream[offset + 5].toLong() and 0xFF) shl 40) or
            ((stream[offset + 6].toLong() and 0xFF) shl 48) or
            ((stream[offset + 7].toLong() and 0xFF) shl 56)
        return Double.fromBits(bits)
    }

    private companion object {
        const val RECORD_BOF = 0x0809
        const val RECORD_EOF = 0x000A
        const val RECORD_CODEPAGE = 0x0042
        const val RECORD_DATEMODE = 0x0022
        const val RECORD_SST = 0x00FC
        const val RECORD_CONTINUE = 0x003C
        const val RECORD_FORMAT = 0x041E
        const val RECORD_XF = 0x00E0
        const val RECORD_BOUNDSHEET = 0x0085
        const val RECORD_LABEL_SST = 0x00FD
        const val RECORD_NUMBER = 0x0203
        const val RECORD_RK = 0x027E
        const val RECORD_MUL_RK = 0x00BD
        const val RECORD_BOOL_ERR = 0x0205
        const val RECORD_FORMULA = 0x0006
        const val RECORD_STRING = 0x0207
    }
}

/** Little-endian reader over a byte array used by the OLE2 header parsing. */
internal class ByteReader(
    private val bytes: ByteArray,
    private var position: Int,
) {
    fun skip(count: Int) {
        position += count
    }

    fun u16(): Int {
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun u32(): Int {
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun i32(): Int = u32()
}
