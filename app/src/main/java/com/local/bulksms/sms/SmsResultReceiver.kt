package com.local.bulksms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        SmsResultRegistry.record(token, partIndex, resultCode)
    }

    companion object {
        const val ACTION_SENT = "com.local.bulksms.action.SMS_SENT"
        const val EXTRA_TOKEN = "submission_token"
        const val EXTRA_PART_INDEX = "part_index"
    }
}

internal object SmsResultRegistry {
    private data class Waiter(
        val aggregator: SmsResultAggregator,
        val result: CompletableDeferred<SmsSubmissionResult>,
    )

    private val waiters = ConcurrentHashMap<String, Waiter>()

    fun register(token: String, expectedParts: Int): CompletableDeferred<SmsSubmissionResult> {
        val result = CompletableDeferred<SmsSubmissionResult>()
        check(waiters.putIfAbsent(token, Waiter(SmsResultAggregator(expectedParts), result)) == null) {
            "重复的短信提交令牌"
        }
        return result
    }

    fun record(token: String, partIndex: Int, resultCode: Int) {
        val waiter = waiters[token] ?: return
        if (partIndex < 0) return
        waiter.aggregator.record(partIndex, resultCode)?.let(waiter.result::complete)
    }

    fun unregister(token: String) {
        waiters.remove(token)
    }
}
