package com.local.bulksms.ui

enum class AppDestination(val route: String, val label: String) {
    DATA("data", "数据"),
    SMS("sms", "短信"),
    SETTINGS("settings", "设置"),
}
