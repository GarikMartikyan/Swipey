package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test fun formatsZero() = assertEquals("0 B", formatBytes(0))
    @Test fun formatsBytes() = assertEquals("512 B", formatBytes(512))
    @Test fun formatsKilobytes() = assertEquals("1.0 KB", formatBytes(1024))
    @Test fun formatsMegabytes() = assertEquals("2.5 MB", formatBytes(2_621_440))
    @Test fun formatsGigabytes() = assertEquals("1.2 GB", formatBytes(1_288_490_189))
    @Test fun roundsToOneDecimal() = assertEquals("1.5 KB", formatBytes(1536))
    @Test fun handlesNegativeAsZero() = assertEquals("0 B", formatBytes(-5))
}
