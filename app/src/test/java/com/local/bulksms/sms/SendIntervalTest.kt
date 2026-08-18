package com.local.bulksms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendIntervalTest {
    @Test
    fun defaultIntervalIsShownAsPointThreeSeconds() {
        assertEquals("0.3", formatSendIntervalSeconds(DEFAULT_SEND_INTERVAL_MILLIS))
    }

    @Test
    fun decimalSecondsAreConvertedToMilliseconds() {
        assertEquals(0L, parseSendIntervalMillis("0"))
        assertEquals(1_500L, parseSendIntervalMillis("1.5"))
        assertEquals(60_000L, parseSendIntervalMillis("60"))
    }

    @Test
    fun blankNegativeAndOutOfRangeIntervalsAreRejected() {
        assertNull(parseSendIntervalMillis(""))
        assertNull(parseSendIntervalMillis("-1"))
        assertNull(parseSendIntervalMillis("60.1"))
        assertNull(parseSendIntervalMillis("不是数字"))
    }
}
