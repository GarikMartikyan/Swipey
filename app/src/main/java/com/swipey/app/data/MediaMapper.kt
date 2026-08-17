package com.swipey.app.data

import com.swipey.app.domain.MediaItem

/**
 * Pure mapping decisions for one MediaStore row. Kept separate from cursor
 * iteration so it can be unit tested on the JVM.
 *
 * Returns null for rows that are not worth showing: a zero size usually means a
 * placeholder or still-writing file, and an id of 0 is never valid.
 *
 * ### Absences, not zeroes
 * [widthPx], [heightPx] and [relativePath] describe a row rather than qualify it, so an
 * unusable value here drops the field instead of the item. MediaStore reports `0` for a
 * dimension it never read — common across the Video collection, and true of any image whose
 * header the media scanner could not parse — and a blank string for a path it does not
 * have. Both are turned back into nulls at this boundary, because everything downstream
 * renders a null as "unknown" and a zero as a fact.
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
    widthPx: Int? = null,
    heightPx: Int? = null,
    relativePath: String? = null,
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
        // Independently, so a row that knows one dimension still reports it.
        widthPx = widthPx?.takeIf { it > 0 },
        heightPx = heightPx?.takeIf { it > 0 },
        relativePath = relativePath?.takeIf { it.isNotBlank() },
    )
}
