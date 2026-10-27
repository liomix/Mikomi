package com.liomix.mikomi.feature.detail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相对时间格式化（对齐 Dart 版 `_formatDate`）。
 *
 * 1 周内显示「刚刚 / N分钟前 / N小时前 / N天前」，更早显示完整日期；
 * 时间戳非法（<= 0）返回空串，由调用方决定是否展示。
 */
internal fun formatRelativeTime(timestampSec: Long): String {
    if (timestampSec <= 0) return ""
    val date = Date(timestampSec * 1000)
    val diffMs = Date().time - date.time
    val minutes = diffMs / 60_000
    val hours = diffMs / 3_600_000
    val days = diffMs / 86_400_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }
}
