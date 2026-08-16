package com.swipey.app.data

import com.swipey.app.domain.MediaItem

/**
 * Pure mapping decisions for one MediaStore row. Kept separate from cursor
 * iteration so it can be unit tested on the JVM.
 *
 * Returns null for rows that are not worth showing: a zero size usually means a
 * placeholder or still-writing file, and an id of 0 is never valid.
 */
fun mapMediaRow(
    id: Long,
    isVideo: Boolean,
    sizeBytes: Long,
    dateAddedSec: Long,
    durationMs: Long?,
    bucketId: Long,
    bucketName: String?,
    displayName: String?,
): MediaItem? {
    if (id <= 0L || sizeBytes <= 0L) return null
    return MediaItem(
        id = id,
        isVideo = isVideo,
        sizeBytes = sizeBytes,
        dateAddedSec = dateAddedSec,
        durationMs = durationMs?.takeIf { it > 0L },
        bucketId = bucketId,
        bucketName = bucketName ?: "Unknown album",
        displayName = displayName ?: "Unnamed",
    )
}
