package com.local.bulksms.sms

import android.app.Activity
import android.telephony.SmsManager
import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SmsResultAggregatorTest {
    @Test
    fun notificationPermissionIsRequestedOnlyFromApi33() {
        assertEquals(
            setOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            SmsPermissions.requiredRuntimePermissions(sdkInt = 32),
        )
        assertEquals(
            setOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            SmsPermissions.requiredRuntimePermissions(sdkInt = 33),
        )
    }

    @Test
    fun multipartSucceedsOnlyWhenEveryPartSucceeds() {
        val aggregator = SmsResultAggregator(expectedParts = 3)

        assertNull(aggregator.record(0, Activity.RESULT_OK))
        assertNull(aggregator.record(1, SmsManager.RESULT_ERROR_NO_SERVICE))
        val result = requireNotNull(aggregator.record(2, Activity.RESULT_OK))

        assertFalse(result.success)
        assertEquals(SmsManager.RESULT_ERROR_NO_SERVICE, result.errorCode)
    }
}
