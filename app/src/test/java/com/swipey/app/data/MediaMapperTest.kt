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

    // --- dimensions and path: what the info sheet reads, and what MediaStore may withhold ---

    @Test fun carriesDimensionsAndPathWhenPresent() {
        val item = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
            widthPx = 4032, heightPx = 3024, relativePath = "DCIM/Camera/",
        )
        assertEquals(4032, item?.widthPx)
        assertEquals(3024, item?.heightPx)
        assertEquals("DCIM/Camera/", item?.relativePath)
    }

    /**
     * MediaStore reports 0 rather than null for a dimension it never read — which is most
     * of the Video collection on some devices, and any image whose header the scanner could
     * not parse. Zero is not a width; carrying it through would put "0 x 0 - 0.0 MP" in the
     * sheet, which reads as a fact rather than as an absence.
     */
    @Test fun treatsZeroAndNegativeDimensionsAsUnknown() {
        val zero = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
            widthPx = 0, heightPx = 0, relativePath = null,
        )
        assertNull(zero?.widthPx)
        assertNull(zero?.heightPx)

        val negative = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
            widthPx = -1, heightPx = -1, relativePath = null,
        )
        assertNull(negative?.widthPx)
        assertNull(negative?.heightPx)
    }

    @Test fun keepsOneDimensionWhenOnlyTheOtherIsMissing() {
        val item = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
            widthPx = 4032, heightPx = 0, relativePath = null,
        )
        assertEquals("a known width is still worth stating", 4032, item?.widthPx)
        assertNull(item?.heightPx)
    }

    @Test fun treatsABlankPathAsUnknown() {
        val item = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
            widthPx = null, heightPx = null, relativePath = "   ",
        )
        assertNull(item?.relativePath)
    }

    @Test fun dimensionsAndPathDefaultToUnknown() {
        val item = mapMediaRow(
            id = 9L, isVideo = false, sizeBytes = 100L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
        )
        assertNull(item?.widthPx)
        assertNull(item?.heightPx)
        assertNull(item?.relativePath)
    }
}
