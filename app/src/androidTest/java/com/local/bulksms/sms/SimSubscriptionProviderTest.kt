package com.local.bulksms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimSubscriptionProviderTest {
    @Test
    fun activeSubscriptionsMapToStableRadioOptions() {
        val provider = SimSubscriptionProvider {
            listOf(
                SubscriptionSnapshot(22, "", "运营商乙", 1),
                SubscriptionSnapshot(-1, "无效", "", 2),
                SubscriptionSnapshot(11, "工作卡", "运营商甲", 0),
            )
        }

        val options = provider.active()

        assertEquals(listOf(11, 22), options.map { it.subscriptionId })
        assertEquals("工作卡", options[0].displayLabel)
        assertEquals("运营商乙", options[1].displayLabel)
        assertTrue(options.all { it.displayLabel.isNotBlank() })
    }

    @Test
    fun permissionFailureIsNotMisreportedAsAnEmptySimList() {
        val provider = SimSubscriptionProvider {
            throw SecurityException("READ_PHONE_STATE denied")
        }

        assertThrows(SecurityException::class.java) { provider.active() }
    }
}
