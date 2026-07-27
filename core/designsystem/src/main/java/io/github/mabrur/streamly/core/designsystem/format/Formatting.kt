package io.github.mabrur.streamly.core.designsystem.format

import java.util.Locale

fun formatViewCount(count: Long): String {
    val label = when {
        count >= 1_000_000_000 -> compact(count, 1_000_000_000, "B")
        count >= 1_000_000 -> compact(count, 1_000_000, "M")
        count >= 1_000 -> compact(count, 1_000, "K")
        else -> count.toString()
    }
    return if (count == 1L) "$label view" else "$label views"
}

private fun compact(count: Long, unit: Long, suffix: String): String {
    val whole = count / unit
    val roundedTenths = ((count % unit) * 10 + unit / 2) / unit
    return when {
        whole >= 10 || roundedTenths == 0L -> "$whole$suffix"
        roundedTenths == 10L -> "${whole + 1}$suffix"
        else -> String.format(Locale.US, "%d.%d%s", whole, roundedTenths, suffix)
    }
}

fun formatRelativeAge(epochSeconds: Long, nowSeconds: Long): String {
    val elapsed = (nowSeconds - epochSeconds).coerceAtLeast(0)
    return when {
        elapsed < 60 -> "just now"
        elapsed < 3_600 -> plural(elapsed / 60, "minute")
        elapsed < 86_400 -> plural(elapsed / 3_600, "hour")
        elapsed < 2_592_000 -> plural(elapsed / 86_400, "day")
        elapsed < 31_536_000 -> plural(elapsed / 2_592_000, "month")
        else -> plural(elapsed / 31_536_000, "year")
    }
}

private fun plural(value: Long, unit: String): String =
    if (value == 1L) "1 $unit ago" else "$value ${unit}s ago"

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * "0 B", "512 B", "1.4 MB". Binary units, one decimal above KB.
 *
 * Bytes are shown whole — "1.0 B" would be nonsense — so the decimal only starts at KB.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
