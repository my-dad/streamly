package io.github.mabrur.streamly.core.designsystem.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `view counts below a thousand render exactly`() {
        assertEquals("0 views", formatViewCount(0))
        assertEquals("1 view", formatViewCount(1))
        assertEquals("999 views", formatViewCount(999))
    }

    @Test
    fun `view counts in thousands render with K`() {
        assertEquals("1K views", formatViewCount(1_000))
        assertEquals("1.2K views", formatViewCount(1_234))
        assertEquals("999K views", formatViewCount(999_000))
    }

    @Test
    fun `view counts in millions render with M`() {
        assertEquals("1M views", formatViewCount(1_000_000))
        assertEquals("1.3M views", formatViewCount(1_284_000))
        assertEquals("8.4M views", formatViewCount(8_420_000))
    }

    @Test
    fun `view counts in billions render with B`() {
        assertEquals("2.1B views", formatViewCount(2_100_000_000))
    }

    @Test
    fun `relative age renders just now under a minute`() {
        assertEquals("just now", formatRelativeAge(epochSeconds = 1_000, nowSeconds = 1_030))
    }

    @Test
    fun `relative age renders minutes hours and days`() {
        val now = 1_000_000L
        assertEquals("5 minutes ago", formatRelativeAge(now - 300, now))
        assertEquals("1 minute ago", formatRelativeAge(now - 60, now))
        assertEquals("3 hours ago", formatRelativeAge(now - 10_800, now))
        assertEquals("1 hour ago", formatRelativeAge(now - 3_600, now))
        assertEquals("2 days ago", formatRelativeAge(now - 172_800, now))
        assertEquals("1 day ago", formatRelativeAge(now - 86_400, now))
    }

    @Test
    fun `relative age renders months and years`() {
        val now = 100_000_000L
        assertEquals("2 months ago", formatRelativeAge(now - 5_184_000, now))
        assertEquals("1 year ago", formatRelativeAge(now - 31_536_000, now))
        assertEquals("3 years ago", formatRelativeAge(now - 94_608_000, now))
    }

    @Test
    fun `a future timestamp is clamped to just now rather than going negative`() {
        assertEquals("just now", formatRelativeAge(epochSeconds = 2_000, nowSeconds = 1_000))
    }

    @Test
    fun `durations under an hour render as m colon ss`() {
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("9:56", formatDuration(596_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `durations of an hour or more render as h colon mm colon ss`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:30:00", formatDuration(5_400_000))
        assertEquals("12:15:04", formatDuration(44_104_000))
    }

    @Test
    fun `a zero or negative duration renders as zero`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5_000))
    }

    @Test
    fun `compact count has no unit suffix`() {
        assertEquals("12", formatCompactCount(12))
        assertEquals("1.5K", formatCompactCount(1_500))
        assertEquals("8.4M", formatCompactCount(8_420_000))
    }

    @Test
    fun `formats byte sizes in binary units`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
}
