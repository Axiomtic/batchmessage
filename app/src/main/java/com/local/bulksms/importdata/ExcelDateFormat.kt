package com.local.bulksms.importdata

import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared Excel serial-date handling for both OOXML (.xlsx) and BIFF8 (.xls) importers.
 *
 * Excel stores dates as serial numbers relative to a 1900 or 1904 epoch; whether a
 * numeric cell is a date is decided by its number format. The same format-code rules
 * apply to the OOXML `numFmt` and the BIFF8 `FORMAT` records.
 */
object ExcelDateFormat {
    private const val MILLIS_PER_DAY = 86_400_000L

    private val EXCEL_EPOCH_MILLIS = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(1899, GregorianCalendar.DECEMBER, 31, 0, 0, 0)
    }.timeInMillis

    private val EXCEL_1904_EPOCH_MILLIS = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(1904, GregorianCalendar.JANUARY, 1, 0, 0, 0)
    }.timeInMillis

    /**
     * Formats an Excel serial date as text, or returns null when the value is not a
     * finite serial that can be rendered. [hasTime] comes from the number format.
     */
    fun format(serialText: String, hasTime: Boolean, date1904: Boolean): String? {
        val serial = serialText.toDoubleOrNull() ?: return null
        if (!serial.isFinite()) return null
        return try {
            var milliseconds = Math.round(serial * MILLIS_PER_DAY)
            if (!date1904 && serial >= 60.0) milliseconds -= MILLIS_PER_DAY
            val epoch = if (date1904) EXCEL_1904_EPOCH_MILLIS else EXCEL_EPOCH_MILLIS
            val date = Date(epoch + milliseconds)
            if (!hasTime) {
                dateFormat("yyyy-MM-dd").format(date)
            } else {
                val full = dateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date)
                if (full.endsWith(".000")) full.removeSuffix(".000") else full
            }
        } catch (_: ArithmeticException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun isDateFormat(formatCode: String): Boolean {
        val stripped = formatCode
            .replace(Regex("\\\"[^\\\"]*\\\""), "")
            .replace(Regex("\\\\."), "")
            .lowercase(Locale.US)
        return stripped.any { it == 'y' || it == 'd' || it == 'h' || it == 's' } ||
            (stripped.contains('m') && !stripped.matches(Regex(".*[0#?].*")))
    }

    fun hasTimePart(formatCode: String): Boolean {
        val stripped = formatCode.lowercase(Locale.US)
        return stripped.contains('h') || stripped.contains('s') || stripped.contains("am/pm")
    }

    /** Built-in number-format ids that render dates. */
    fun builtInDateFormat(numFmtId: Int): String? = when (numFmtId) {
        14 -> "yyyy-mm-dd"
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "m/d/yy h:mm"
        27, 28, 29 -> "yyyy-mm-dd"
        30 -> "m-d-yy"
        31 -> "yyyy-mm-dd"
        32 -> "h:mm"
        33 -> "h:mm:ss"
        34 -> "h:mm"
        35 -> "h:mm:ss"
        36 -> "yyyy-mm"
        45 -> "mm:ss"
        46 -> "[h]:mm:ss"
        47 -> "mmss.0"
        50, 51, 52, 53, 54, 55, 56, 57, 58 -> "yyyy-mm-dd"
        else -> null
    }

    private fun dateFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}
