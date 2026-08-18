package com.local.bulksms.ui.send

import com.local.bulksms.model.SendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendProgressTest {
    @Test
    fun terminalStatusesBecomeFinalSuccessAndFailureCounts() {
        val progress = SendProgressUiState.from(
            listOf(SendStatus.SUBMITTED, SendStatus.FAILED, SendStatus.UNCERTAIN),
        )

        assertEquals(3, progress.total)
        assertEquals(3, progress.processed)
        assertEquals(1, progress.succeeded)
        assertEquals(2, progress.failed)
        assertFalse(progress.running)
    }

    @Test
    fun pendingOrSubmittingStatusKeepsProgressRunning() {
        val progress = SendProgressUiState.from(
            listOf(SendStatus.SUBMITTED, SendStatus.SUBMITTING, SendStatus.PENDING),
        )

        assertEquals(3, progress.total)
        assertEquals(1, progress.processed)
        assertTrue(progress.running)
    }
}
