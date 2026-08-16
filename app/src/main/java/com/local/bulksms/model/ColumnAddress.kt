package com.local.bulksms.model

fun columnAddress(index: Int): String {
    require(index >= 0) { "列索引不能为负数" }
    var value = index + 1
    return buildString {
        while (value > 0) {
            value--
            append(('A'.code + value % 26).toChar())
            value /= 26
        }
    }.reversed()
}
