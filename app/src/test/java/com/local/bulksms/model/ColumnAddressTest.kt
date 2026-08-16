package com.local.bulksms.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnAddressTest {
    @Test
    fun addressesContinueAfterZ() {
        assertEquals("A", columnAddress(0))
        assertEquals("Z", columnAddress(25))
        assertEquals("AA", columnAddress(26))
        assertEquals("AB", columnAddress(27))
    }
}
