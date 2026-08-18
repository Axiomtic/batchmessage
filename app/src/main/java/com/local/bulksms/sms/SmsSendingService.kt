package com.local.bulksms.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.local.bulksms.BulkSmsApplication
import com.local.bulksms.MainActivity
import com.local.bulksms.data.SendItemEntity
import com.local.bulksms.model.SendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsSendingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private var activeTaskId: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val taskId = command.getStringExtra(EXTRA_TASK_ID)
        if (taskId.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (activeTaskId != null) return START_REDELIVER_INTENT
        activeTaskId = taskId
        val sendIntervalMillis = command.getLongExtra(
            EXTRA_SEND_INTERVAL_MILLIS,
            DEFAULT_SEND_INTERVAL_MILLIS,
        ).coerceIn(0L, MAX_SEND_INTERVAL_MILLIS)
        startAsForeground(progressNotification(processed = 0, total = 0))

        val app = application as BulkSmsApplication
        serviceScope.launch {
            try {
                SmsQueueProcessor(
                    repository = app.repository,
                    gateway = app.smsGateway,
                    sendIntervalMillis = sendIntervalMillis,
                    onProgress = { items ->
                        runCatching {
                            notificationManager.notify(NOTIFICATION_ID, notificationFor(items))
                        }
                        Unit
                    },
                ).process(taskId)
            } finally {
                activeTaskId = null
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notificationFor(items: List<SendItemEntity>): Notification {
        val processed = items.count {
            it.status == SendStatus.SUBMITTED || it.status == SendStatus.FAILED ||
                it.status == SendStatus.UNCERTAIN || it.status == SendStatus.CANCELLED
        }
        val succeeded = items.count { it.status == SendStatus.SUBMITTED }
        val failed = items.count {
            it.status == SendStatus.FAILED || it.status == SendStatus.UNCERTAIN ||
                it.status == SendStatus.CANCELLED
        }
        return if (processed < items.size) {
            progressNotification(processed, items.size)
        } else {
            baseNotification()
                .setContentTitle("短信发送完成")
                .setContentText("成功 $succeeded 条，失败 $failed 条")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .build()
        }
    }

    private fun progressNotification(processed: Int, total: Int): Notification = baseNotification()
        .setContentTitle("正在发送短信")
        .setContentText(if (total == 0) "正在准备发送" else "正在发送 $processed/$total")
        .setProgress(total.coerceAtLeast(1), processed, total == 0)
        .setOngoing(true)
        .build()

    private fun baseNotification(): Notification.Builder {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "短信发送进度",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val EXTRA_TASK_ID = "send_task_id"
        private const val EXTRA_SEND_INTERVAL_MILLIS = "send_interval_millis"
        private const val CHANNEL_ID = "sms_sending"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, taskId: String, sendIntervalMillis: Long) {
            val intent = Intent(context, SmsSendingService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(
                    EXTRA_SEND_INTERVAL_MILLIS,
                    sendIntervalMillis.coerceIn(0L, MAX_SEND_INTERVAL_MILLIS),
                )
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
