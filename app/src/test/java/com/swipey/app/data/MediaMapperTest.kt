package com.swipey.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMapperTest {
    @Test fun mapsAValidImageRow() {
        val item = mapMediaRow(
            id = 7L, isVideo = false, sizeBytes = 1234L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
        )
        assertEquals(7L, item?.id)
        assertEquals(false, item?.isVideo)
        assertEquals("Camera", item?.bucketName)
        assertNull(item?.durationMs)
    }

    @Test fun mapsAValidVideoRowWithDuration() {
        val item = mapMediaRow(
            id = 8L, isVideo = true, sizeBytes = 50L, dateAddedSec = 900L,
            durationMs = 12_000L, bucketId = 3L, bucketName = "Camera", displayName = "V.mp4",
        )
        assertEquals(true, item?.isVideo)
        assertEquals(12_000L, item?.durationMs)
    }

    @Test fun rejectsZeroSizedRows() {
        assertNull(mapMediaRow(1L, false, 0L, 900L, null, 3L, "Camera", "a.jpg"))
    }

    @Test fun rejectsInvalidIds() {
        assertNull(mapMediaRow(0L, false, 100L, 900L, null, 3L, "Camera", "a.jpg"))
    }

    @Test fun fallsBackWhenBucketNameIsNull() {
        assertEquals("Unknown album", mapMediaRow(1L, false, 100L, 900L, null, 3L, null, "a.jpg")?.bucketName)
    }

    @Test fun fallsBackWhenDisplayNameIsNull() {
        assertEquals("Unnamed", mapMediaRow(1L, false, 100L, 900L, null, 3L, "Camera", null)?.displayName)
    }

    @Test fun treatsZeroDurationAsNull() {
        assertNull(mapMediaRow(1L, true, 100L, 900L, 0L, 3L, "Camera", "v.mp4")?.durationMs)
    }
}
