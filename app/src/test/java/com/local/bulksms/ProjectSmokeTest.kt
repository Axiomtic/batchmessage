package com.local.bulksms

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationClassExists() {
        assertEquals("BulkSmsApplication", BulkSmsApplication::class.simpleName)
    }
}
