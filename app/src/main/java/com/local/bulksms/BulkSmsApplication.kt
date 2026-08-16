package com.local.bulksms

import android.app.Application
import com.local.bulksms.data.AppDatabase
import com.local.bulksms.data.BulkSmsRepository
import com.local.bulksms.sms.AndroidSmsGateway
import com.local.bulksms.sms.SmsGateway

class BulkSmsApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: BulkSmsRepository by lazy { BulkSmsRepository(database) }
    private val androidSmsGateway: SmsGateway by lazy { AndroidSmsGateway(this) }

    @Volatile
    var smsGatewayOverride: SmsGateway? = null

    val smsGateway: SmsGateway
        get() = smsGatewayOverride ?: androidSmsGateway
}
