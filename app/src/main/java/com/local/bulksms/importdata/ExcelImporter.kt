package com.local.bulksms.importdata

import com.local.bulksms.model.RawTable
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Unified Excel entry point.
 *
 * Apache POI ([PoiExcelImporter]) is tried first because it is the reference
 * implementation for the many real-world .xls/.xlsx variants. If POI rejects the
 * container, the self-hosted readers ([XlsxImporter] / [XlsImporter]) are used as a
 * fallback so documents with a stray DOCTYPE or other minor anomalies still import.
 */
object ExcelImporter : TableImporter {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04"
    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
    )

    override fun import(input: InputStream): RawTable {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        val bytes = buffered.readBytes()

        val poiError = try {
            return PoiExcelImporter().import(ByteArrayInputStream(bytes))
        } catch (exception: Exception) {
            exception
        }

        // Fall back to the self-hosted readers for containers POI does not accept.
        try {
            return selfHostedImport(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            throw poiError
        }
    }

    private fun selfHostedImport(input: InputStream): RawTable {
        val buffered = BufferedInputStream(input)
        buffered.mark(8)
        val magic = ByteArray(8)
        val read = buffered.read(magic)
        buffered.reset()
        return when {
            read >= 4 && magicMatches(magic, ZIP_MAGIC) -> XlsxImporter().import(buffered)
            read >= 8 && magicMatches(magic, OLE2_MAGIC) -> XlsImporter().import(buffered)
            else -> throw IllegalArgumentException("无法识别的 Excel 文件格式，请使用 .xlsx 或 .xls 文件")
        }
    }

    private fun magicMatches(candidate: ByteArray, magic: ByteArray): Boolean =
        magic.indices.all { index -> candidate[index] == magic[index] }
}
