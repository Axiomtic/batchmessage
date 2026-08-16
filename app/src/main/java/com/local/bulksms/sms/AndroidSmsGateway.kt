package com.local.bulksms.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AndroidSmsGateway(
    context: Context,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) : SmsGateway {
    private val appContext = context.applicationContext
    private val systemManager = appContext.getSystemService(SmsManager::class.java)

    override fun segmentCount(body: String, subscriptionId: Int): Int =
        manager(subscriptionId).divideMessage(body).size

    override suspend fun submit(submission: SmsSubmission): SmsSubmissionResult {
        require(submission.phone.isNotBlank()) { "收件号码不能为空" }
        require(submission.body.isNotBlank()) { "短信正文不能为空" }
        require(submission.subscriptionId >= 0) { "SIM 订阅 ID 无效" }

        val manager = manager(submission.subscriptionId)
        val parts = manager.divideMessage(submission.body)
        if (parts.isEmpty()) return SmsSubmissionResult(false, ERROR_EMPTY_SEGMENTS)

        val token = tokenFactory()
        val result = SmsResultRegistry.register(token, parts.size)
        return try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { partIndex ->
                val intent = Intent(appContext, SmsResultReceiver::class.java).apply {
                    action = SmsResultReceiver.ACTION_SENT
                    putExtra(SmsResultReceiver.EXTRA_TOKEN, token)
                    putExtra(SmsResultReceiver.EXTRA_PART_INDEX, partIndex)
                }
                sentIntents += PendingIntent.getBroadcast(
                    appContext,
                    REQUEST_CODES.getAndIncrement(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            manager.sendMultipartTextMessage(
                submission.phone,
                null,
                parts,
                sentIntents,
                null,
            )
            result.await()
        } catch (_: SecurityException) {
            SmsSubmissionResult(false, ERROR_PERMISSION_DENIED)
        } catch (_: IllegalArgumentException) {
            SmsSubmissionResult(false, ERROR_INVALID_ARGUMENT)
        } finally {
            SmsResultRegistry.unregister(token)
        }
    }

    @Suppress("DEPRECATION")
    private fun manager(subscriptionId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            systemManager.createForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    companion object {
        const val ERROR_EMPTY_SEGMENTS = -10_001
        const val ERROR_PERMISSION_DENIED = -10_002
        const val ERROR_INVALID_ARGUMENT = -10_003
        private val REQUEST_CODES = AtomicInteger(1)
    }
}
