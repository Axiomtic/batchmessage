package com.local.bulksms.sms

import android.app.Activity
import android.Manifest
import android.os.Build

object SmsPermissions {
    fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Set<String> = buildSet {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

data class SmsSubmission(
    val itemId: String,
    val subscriptionId: Int,
    val phone: String,
    val body: String,
)

data class SmsSubmissionResult(
    val success: Boolean,
    val errorCode: Int? = null,
)

interface SmsGateway {
    suspend fun submit(submission: SmsSubmission): SmsSubmissionResult

    fun segmentCount(body: String, subscriptionId: Int): Int
}

class SmsResultAggregator(private val expectedParts: Int) {
    private val results = mutableMapOf<Int, Int>()
    private var completed = false

    init {
        require(expectedParts > 0) { "短信分段数必须大于 0" }
    }

    @Synchronized
    fun record(partIndex: Int, resultCode: Int): SmsSubmissionResult? {
        require(partIndex in 0 until expectedParts) { "短信分段索引超出范围" }
        if (completed || results.containsKey(partIndex)) return null
        results[partIndex] = resultCode
        if (results.size < expectedParts) return null

        completed = true
        val error = (0 until expectedParts)
            .mapNotNull(results::get)
            .firstOrNull { it != Activity.RESULT_OK }
        return SmsSubmissionResult(success = error == null, errorCode = error)
    }
}
