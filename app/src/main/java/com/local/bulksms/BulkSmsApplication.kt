package com.local.bulksms

import android.app.Application
import com.local.bulksms.data.AppDatabase
import com.local.bulksms.data.BulkSmsRepository

class BulkSmsApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: BulkSmsRepository by lazy { BulkSmsRepository(database) }
}
