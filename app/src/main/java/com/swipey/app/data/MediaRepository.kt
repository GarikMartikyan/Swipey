package com.swipey.app.data

import android.content.ContentResolver
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import com.swipey.app.domain.LiveTrashRow
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.chunkedForRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val resolver: ContentResolver) {

    /**
     * Base projection shared by the Images and Video collections. DURATION is
     * NOT included here: it is not a valid column on the Images collection and
     * MediaProvider validates projections strictly, throwing IllegalArgumentException
     * on an unknown column. See Ruling R3.
     */
    private val baseProjection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.DISPLAY_NAME,
    )

    /** Video-only projection: base + DURATION, valid on the Video collection. */
    private val videoProjection = baseProjection + MediaStore.MediaColumns.DURATION

    private val trashProjection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.IS_TRASHED,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_EXPIRES,
    )

    /**
     * Every non-trashed image and video. No match-trashed argument is passed:
     * on collection URIs MATCH_DEFAULT resolves to MATCH_EXCLUDE, so trashed
     * items are excluded for free (spec §5.2).
     */
    suspend fun queryAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryMedia(isVideo = false) + queryMedia(isVideo = true)
    }

    private fun queryMedia(isVideo: Boolean): List<MediaItem> {
        val projection = if (isVideo) videoProjection else baseProjection
        val items = mutableListOf<MediaItem>()
        resolver.query(collectionUriFor(isVideo), projection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationCol = if (isVideo) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
            val bucketIdCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) {
                mapMediaRow(
                    id = c.getLong(idCol),
                    isVideo = isVideo,
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(dateCol),
                    durationMs = if (isVideo) durationCol.takeIf { it >= 0 }?.let { c.getLongOrNull(it) } else null,
                    bucketId = bucketIdCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    bucketName = bucketNameCol.takeIf { it >= 0 }?.let { c.getString(it) },
                    displayName = nameCol.takeIf { it >= 0 }?.let { c.getString(it) },
                )?.let(items::add)
            }
        }
        return items
    }

    /** Everything currently in the system trash that this app can see (spec §5.2). */
    suspend fun queryTrashed(): Map<Long, LiveTrashRow> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        (readTrashRows(false, args) + readTrashRows(true, args)).associateBy { it.mediaId }
    }

    /**
     * Verification after a trash or restore. MATCH_INCLUDE is required: a bare
     * collection query resolves to MATCH_EXCLUDE and would report every successful
     * trash as a failure (spec §8.2). An absent row means "gone", not "not trashed".
     *
     * Whole-branch review, I4: chunked. The 500-URI limit of §13 was applied only to
     * `createTrashRequest`, never here — so binning 1,500 items built a single ~12 KB
     * selection with 1,500 `IN`-list terms, untested at that scale. This is the one query
     * the entire recoverability guarantee rests on: an `IllegalArgumentException` from an
     * oversized selection would abort the pass, and (worse, if the limit were ever hit
     * silently) a truncated result reads as "these rows are absent" — which resolves to
     * Vanished and deletes the records. Reusing the same [chunkedForRequest] boundary
     * keeps one number to reason about; the per-chunk results are merged, and an id can
     * appear in at most one chunk, so the merge is collision-free.
     */
    suspend fun verify(ids: List<Long>, isVideo: Boolean): Map<Long, LiveTrashRow> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val rows = mutableListOf<LiveTrashRow>()
            ids.chunkedForRequest().forEach { chunk ->
                val args = Bundle().apply {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                    )
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
                rows += readTrashRows(isVideo, args)
            }
            rows.associateBy { it.mediaId }
        }

    private fun readTrashRows(isVideo: Boolean, args: Bundle): List<LiveTrashRow> {
        val rows = mutableListOf<LiveTrashRow>()
        resolver.query(collectionUriFor(isVideo), trashProjection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val trashedCol = c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val expiresCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                rows += LiveTrashRow(
                    mediaId = c.getLong(idCol),
                    isTrashed = trashedCol.takeIf { it >= 0 }?.let { c.getInt(it) == 1 } ?: false,
                    displayName = nameCol.takeIf { it >= 0 }?.let { c.getString(it) } ?: "Unnamed",
                    sizeBytes = sizeCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    dateExpiresSec = expiresCol.takeIf { it >= 0 }?.let { c.getLongOrNull(it) },
                )
            }
        }
        return rows
    }

    private fun Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
}
