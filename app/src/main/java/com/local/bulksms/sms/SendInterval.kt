package com.local.bulksms.sms

import android.content.Context
import kotlin.math.roundToLong

const val DEFAULT_SEND_INTERVAL_MILLIS = 300L
const val MAX_SEND_INTERVAL_MILLIS = 60_000L

fun parseSendIntervalMillis(input: String): Long? {
    val seconds = input.trim().toDoubleOrNull() ?: return null
    if (!seconds.isFinite() || seconds < 0.0 || seconds > 60.0) return null
    return (seconds * 1_000.0).roundToLong()
}

fun formatSendIntervalSeconds(intervalMillis: Long): String {
    val safeMillis = intervalMillis.coerceIn(0L, MAX_SEND_INTERVAL_MILLIS)
    return if (safeMillis % 1_000L == 0L) {
        (safeMillis / 1_000L).toString()
    } else {
        (safeMillis / 1_000.0).toString().trimEnd('0').trimEnd('.')
    }
}

class SendPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var sendIntervalMillis: Long
        get() = preferences.getLong(KEY_SEND_INTERVAL_MILLIS, DEFAULT_SEND_INTERVAL_MILLIS)
            .coerceIn(0L, MAX_SEND_INTERVAL_MILLIS)
        set(value) {
            preferences.edit()
                .putLong(KEY_SEND_INTERVAL_MILLIS, value.coerceIn(0L, MAX_SEND_INTERVAL_MILLIS))
                .apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "send_preferences"
        const val KEY_SEND_INTERVAL_MILLIS = "send_interval_millis"
    }
}
