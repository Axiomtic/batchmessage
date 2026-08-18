package com.local.bulksms.importdata

import java.io.ByteArrayOutputStream

/**
 * Shared builders for constructing a minimal OLE2 compound document containing a
 * BIFF8 workbook stream, so the .xls importers can be tested without binary assets.
 */
internal object XlsFixture {

    fun ole2(workbookBytes: ByteArray): ByteArray {
        val sectorSize = 512
        // Pad past the mini-stream cutoff so the stream lives on the regular FAT chain;
        // BIFF parsing stops at EOF so the padding is harmless.
        val padded = workbookBytes.copyOf(maxOf(workbookBytes.size, 4096))
        val dataSectorCount = padded.size / sectorSize
        val totalSectors = 3 + dataSectorCount
        val fat = IntArray(totalSectors) { -1 }
        fat[0] = -3
        fat[1] = -3
        fat[2] = -2
        for (index in 0 until dataSectorCount) {
            fat[3 + index] = if (index == dataSectorCount - 1) -2 else 3 + index + 1
        }
        val output = ByteArrayOutputStream()
        output.write(header())
        output.write(fatSector(fat))
        output.write(directorySector(padded.size.toLong()))
        output.write(padded)
        return output.toByteArray()
    }

    fun workbook(
        globals: List<ByteArray>,
        sheet: List<ByteArray>,
        hiddenSheet: List<ByteArray> = emptyList(),
    ): ByteArray {
        // Keep the globals in their original order; BOUNDSHEET lbPlyPos fields are
        // patched to the absolute offsets of the hidden (if any) and visible sheets.
        var cursor = globals.sumOf { it.size }
        val hiddenStart = if (hiddenSheet.isNotEmpty()) cursor else -1
        if (hiddenSheet.isNotEmpty()) cursor += hiddenSheet.sumOf { it.size }
        val visibleStart = cursor
        var boundsheetIndex = 0
        val patchedGlobals = globals.map { recordBytes ->
            if (recordBytes.isBoundsheet()) {
                val offset = if (boundsheetIndex == 0 && hiddenSheet.isNotEmpty()) {
                    hiddenStart
                } else {
                    visibleStart
                }
                boundsheetIndex++
                patchBoundsheetOffset(recordBytes, offset)
            } else {
                recordBytes
            }
        }
        return (patchedGlobals + hiddenSheet + sheet).flatMap(ByteArray::toList).toByteArray()
    }

    fun bof(substream: Int): ByteArray = record(0x0809, short(substream) + short(0x0006))

    fun eof(): ByteArray = record(0x000A, ByteArray(0))

    fun codepage(page: Int): ByteArray = record(0x0042, short(page))

    fun datemode(mode: Int): ByteArray = record(0x0022, short(mode))

    fun format(formatId: Int, code: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(short(formatId))
        body.write(short(code.length))
        body.write(byte(0x00))
        body.write(code.toByteArray(Charsets.UTF_16LE))
        return record(0x041E, body.toByteArray())
    }

    fun xf(formatId: Int): ByteArray =
        record(0x00E0, short(0) + short(formatId) + short(0) + ByteArray(14))

    fun sst(strings: List<String>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(int(strings.size))
        body.write(int(strings.size))
        strings.forEach { body.write(biffString(it)) }
        return record(0x00FC, body.toByteArray())
    }

    fun biffString(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(short(value.length))
        out.write(byte(0x00)) // uncompressed UTF-16LE
        out.write(value.toByteArray(Charsets.UTF_16LE))
        return out.toByteArray()
    }

    fun boundsheet(name: String, hidden: Boolean = false): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(int(0)) // lbPlyPos patched later
        body.write(byte(if (hidden) 0x01 else 0x00))
        body.write(byte(0x00))
        body.write(byte(name.length))
        body.write(byte(0x00))
        body.write(name.toByteArray(Charsets.UTF_16LE))
        return record(0x0085, body.toByteArray())
    }

    fun labelSst(row: Int, column: Int, sstIndex: Int): ByteArray =
        record(0x00FD, short(row) + short(column) + short(0) + int(sstIndex))

    fun number(row: Int, column: Int, value: Double): ByteArray =
        record(0x0203, short(row) + short(column) + short(0) + double(value))

    fun numberWithXf(row: Int, column: Int, xf: Int, value: Double): ByteArray =
        record(0x0203, short(row) + short(column) + short(xf) + double(value))

    fun rk(row: Int, column: Int, value: Int): ByteArray =
        record(0x027E, short(row) + short(column) + short(0) + int(value))

    fun mulrk(row: Int, columnFirst: Int, values: List<Pair<Int, Int>>, columnLast: Int): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(short(row))
        body.write(short(columnFirst))
        values.forEach { (xf, rkValue) ->
            body.write(short(xf))
            body.write(int(rkValue))
        }
        body.write(short(columnLast))
        return record(0x00BD, body.toByteArray())
    }

    fun formulaDouble(row: Int, column: Int, value: Double): ByteArray =
        record(0x0006, short(row) + short(column) + short(0) + double(value))

    fun formulaString(row: Int, column: Int): ByteArray =
        record(0x0006, short(row) + short(column) + short(0) + doubleBits(0x0000FFFFL))

    fun formulaBoolean(row: Int, column: Int, value: Boolean): ByteArray =
        record(
            0x0006,
            short(row) + short(column) + short(0) +
                doubleBits((if (value) 1L else 0L) shl 32 or 0x0001FFFFL),
        )

    fun formulaError(row: Int, column: Int, code: Int): ByteArray =
        record(
            0x0006,
            short(row) + short(column) + short(0) +
                doubleBits((code.toLong() and 0xFFFF) shl 32 or 0x0002FFFFL),
        )

    fun stringResult(value: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(byte(value.length))
        body.write(byte(0x00))
        body.write(value.toByteArray(Charsets.UTF_16LE))
        return record(0x0207, body.toByteArray())
    }

    fun record(type: Int, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byte(type))
        out.write(byte(type shr 8))
        out.write(byte(data.size))
        out.write(byte(data.size shr 8))
        out.write(data)
        return out.toByteArray()
    }

    fun short(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    fun int(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    fun byte(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte())

    fun double(value: Double): ByteArray = doubleBits(value.toBits())

    fun doubleBits(bits: Long): ByteArray = byteArrayOf(
        (bits and 0xFF).toByte(),
        ((bits shr 8) and 0xFF).toByte(),
        ((bits shr 16) and 0xFF).toByte(),
        ((bits shr 24) and 0xFF).toByte(),
        ((bits shr 32) and 0xFF).toByte(),
        ((bits shr 40) and 0xFF).toByte(),
        ((bits shr 48) and 0xFF).toByte(),
        ((bits shr 56) and 0xFF).toByte(),
    )

    private fun ByteArray.isBoundsheet(): Boolean =
        size >= 4 && (this[0].toInt() and 0xFF) == 0x85 && (this[1].toInt() and 0xFF) == 0x00

    private fun patchBoundsheetOffset(bytes: ByteArray, offset: Int): ByteArray {
        val patched = bytes.copyOf()
        patched[4] = (offset and 0xFF).toByte()
        patched[5] = ((offset shr 8) and 0xFF).toByte()
        patched[6] = ((offset shr 16) and 0xFF).toByte()
        patched[7] = ((offset shr 24) and 0xFF).toByte()
        return patched
    }

    private fun header(): ByteArray {
        val header = ByteArray(512)
        val signature = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        )
        System.arraycopy(signature, 0, header, 0, 8)
        putShort(header, 0x18, 0x003E)
        putShort(header, 0x1A, 0x0003)
        putShort(header, 0x1C, 0xFFFE)
        putShort(header, 0x1E, 0x0009)
        putShort(header, 0x20, 0x0006)
        putInt(header, 0x2C, 1)
        putInt(header, 0x30, 2)
        putInt(header, 0x38, 4096)
        putInt(header, 0x3C, -2)
        putInt(header, 0x44, -2)
        putInt(header, 0x4C, 1)
        for (index in 1 until 109) {
            putInt(header, 0x4C + index * 4, -1)
        }
        return header
    }

    private fun fatSector(fat: IntArray): ByteArray {
        val sector = ByteArray(512) { 0xFF.toByte() }
        fat.forEachIndexed { index, value ->
            if (index * 4 + 4 <= sector.size) putInt(sector, index * 4, value)
        }
        return sector
    }

    private fun directorySector(workbookSize: Long): ByteArray {
        val sector = ByteArray(512)
        writeDirectoryEntry(sector, 0, "Root Entry", 0x05, -1, -1, 1, -2, 0)
        writeDirectoryEntry(sector, 1, "Workbook", 0x02, -1, -1, -1, 3, workbookSize)
        return sector
    }

    private fun writeDirectoryEntry(
        sector: ByteArray,
        index: Int,
        name: String,
        objectType: Int,
        left: Int,
        right: Int,
        child: Int,
        startSector: Int,
        streamSize: Long,
    ) {
        val offset = index * 128
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        System.arraycopy(nameBytes, 0, sector, offset, minOf(nameBytes.size, 64))
        putShort(sector, offset + 0x40, (name.length + 1) * 2)
        sector[offset + 0x42] = objectType.toByte()
        putInt(sector, offset + 0x44, left)
        putInt(sector, offset + 0x48, right)
        putInt(sector, offset + 0x4C, child)
        putInt(sector, offset + 0x74, startSector)
        putLong(sector, offset + 0x78, streamSize)
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun putLong(bytes: ByteArray, offset: Int, value: Long) {
        putInt(bytes, offset, value.toInt())
        putInt(bytes, offset + 4, (value shr 32).toInt())
    }
}
