package com.local.bulksms.ui

import androidx.annotation.DrawableRes
import com.local.bulksms.ui.icons.BulkSmsIcons

enum class AppDestination(val route: String, val label: String, @DrawableRes val iconRes: Int) {
    DATA("data", "数据", BulkSmsIcons.Data),
    SMS("sms", "发送", BulkSmsIcons.Sms),
    SETTINGS("settings", "设置", BulkSmsIcons.Settings),
}
