package com.swipey.app.domain

import kotlin.random.Random

/**
 * A photo or video. Deliberately holds no android.* types — see spec §5.1.
 * The content URI is derived at the Android edge by MediaItem.contentUri().
 *
 * @property widthPx pixel width, or null where MediaStore has none. Nullable rather than
 *   zero-defaulted because "we do not know" and "it is zero pixels wide" are different
 *   claims, and only one of them is ever true — see `MediaMapper`, which is where the
 *   provider's zeroes are turned back into absences.
 * @property heightPx pixel height, under the same rule. The two are independent: a row can
 *   carry one and not the other, and the info sheet says whichever it has.
 * @property relativePath the folder the file sits in, relative to the storage root
 *   ("DCIM/Camera/"). Null when unknown or blank. Not a filesystem path a caller may open —
 *   scoped storage means only MediaStore can resolve it — so this is for reading, not for
 *   navigating.
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
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val relativePath: String? = null,
)

/**
 * The item's resolution in megapixels, or null unless both dimensions are known.
 *
 * Null rather than zero for a half-known item, which is the whole reason this is a function
 * and not a multiplication at the call site: a photograph whose height MediaStore never
 * recorded has no megapixel count, and printing "4.0 MP" from a width alone would be an
 * invention. Orientation is irrelevant by construction — a portrait and its landscape twin
 * are the same count — which is why nothing here looks at which dimension is larger.
 */
fun MediaItem.megapixels(): Double? {
    val width = widthPx ?: return null
    val height = heightPx ?: return null
    return width.toDouble() * height.toDouble() / 1_000_000.0
}

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

/**
 * A run of items that share a day, for the session grid's headers.
 *
 * @property dayStartSec midnight of that day, in the caller's zone, as Unix seconds. The
 *   grid formats this into a label at the Android edge — the domain has no locale and no
 *   business guessing what "Today" is called.
 * @property items the items on that day, in the order they were given.
 */
data class MediaDay(val dayStartSec: Long, val items: List<MediaItem>)

/**
 * Groups items into days, preserving order.
 *
 * [zoneOffsetSec] is the caller's offset from UTC, passed in rather than read here so this
 * stays free of `java.time` and of the platform's current zone — the same list must group
 * the same way in a test as it does on a phone in Yerevan.
 *
 * Order is preserved rather than sorted: the deck's queue order is the one the grid must
 * show, because the grid is a picture of the session and a session shuffled by seed is not
 * in date order at all. Days therefore appear in the order their first item does, and an
 * out-of-order queue produces a day that appears twice — which is the truth about that
 * queue, not a bug in this function.
 */
fun List<MediaItem>.groupedByDay(zoneOffsetSec: Long): List<MediaDay> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<MediaDay>()
    var currentDay = Long.MIN_VALUE
    var run = mutableListOf<MediaItem>()
    for (item in this) {
        val local = item.dateAddedSec + zoneOffsetSec
        val day = Math.floorDiv(local, SecondsPerDay) * SecondsPerDay - zoneOffsetSec
        if (day != currentDay) {
            if (run.isNotEmpty()) out += MediaDay(currentDay, run)
            currentDay = day
            run = mutableListOf()
        }
        run += item
    }
    if (run.isNotEmpty()) out += MediaDay(currentDay, run)
    return out
}

private const val SecondsPerDay = 86_400L
