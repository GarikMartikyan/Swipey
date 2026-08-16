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

data class Album(
    val bucketId: Long,
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
)

fun List<MediaItem>.toAlbums(): List<Album> =
    groupBy { it.bucketId }
        .map { (bucketId, items) ->
            Album(bucketId, items.first().bucketName, items.size, items.sumOf { it.sizeBytes })
        }
        .sortedByDescending { it.totalBytes }
