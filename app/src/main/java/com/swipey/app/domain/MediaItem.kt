package com.swipey.app.domain

import kotlin.random.Random

/**
 * A photo or video. Deliberately holds no android.* types — see spec §5.1.
 * The content URI is derived at the Android edge by MediaItem.contentUri().
 */
data class MediaItem(
    val id: Long,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val durationMs: Long?,
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
)

enum class SortMode { NEWEST, OLDEST, LARGEST, SMALLEST }

fun List<MediaItem>.sortedFor(mode: SortMode): List<MediaItem> = when (mode) {
    SortMode.NEWEST -> sortedByDescending { it.dateAddedSec }
    SortMode.OLDEST -> sortedBy { it.dateAddedSec }
    SortMode.LARGEST -> sortedByDescending { it.sizeBytes }
    SortMode.SMALLEST -> sortedBy { it.sizeBytes }
}

fun List<MediaItem>.shuffledWithSeed(seed: Long): List<MediaItem> = shuffled(Random(seed))

/**
 * Which item stands for a list of them: the newest, and on a tie the highest id.
 *
 * Both halves matter. `DATE_ADDED` has one-second resolution, so a burst of shots or a
 * bulk import routinely lands several rows on the same second — and `maxByOrNull` alone
 * would then return whichever of them the caller's list happened to hold first, i.e. a
 * cover that changes when MediaStore's row order does. MediaStore hands out `_ID`
 * monotonically, so within one timestamp the larger id is the later insertion: the rule is
 * total, and the same picture comes back every time it is asked.
 *
 * Home asks this twice — once of the whole gallery for its hero, once per bucket for an
 * album's cover — so it lives here rather than being written out at either call site.
 */
fun List<MediaItem>.mostRecent(): MediaItem? =
    maxWithOrNull(compareBy({ it.dateAddedSec }, { it.id }))

/**
 * A folder, and the picture that stands for it.
 *
 * @property coverId the id of the album's [mostRecent] item — the thumbnail Home draws
 *   beside the row. It is a real member of this bucket, never a placeholder.
 * @property coverIsVideo carried alongside [coverId] because a content URI cannot be built
 *   without it: images and videos live in different collections (`data/MediaUri.kt`).
 */
data class Album(
    val bucketId: Long,
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    val coverId: Long,
    val coverIsVideo: Boolean,
)

fun List<MediaItem>.toAlbums(): List<Album> =
    groupBy { it.bucketId }
        .map { (bucketId, items) ->
            // Non-null by construction: groupBy never produces an empty group.
            val cover = items.mostRecent()!!
            Album(
                bucketId = bucketId,
                name = items.first().bucketName,
                itemCount = items.size,
                totalBytes = items.sumOf { it.sizeBytes },
                coverId = cover.id,
                coverIsVideo = cover.isVideo,
            )
        }
        .sortedByDescending { it.totalBytes }
