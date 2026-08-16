package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemTest {
    private fun item(id: Long, size: Long, date: Long, bucket: Long = 1L, bucketName: String = "Camera") =
        MediaItem(id, false, size, date, null, bucket, bucketName, "f$id.jpg")

    @Test fun sortsNewestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 20, 300), item(3, 30, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.NEWEST).map { it.id })
    }

    @Test fun sortsOldestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 20, 300), item(3, 30, 200))
        assertEquals(listOf(1L, 3L, 2L), items.sortedFor(SortMode.OLDEST).map { it.id })
    }

    @Test fun sortsLargestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 30, 300), item(3, 20, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.LARGEST).map { it.id })
    }

    @Test fun sortsSmallestFirst() {
        val items = listOf(item(1, 30, 100), item(2, 10, 300), item(3, 20, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.SMALLEST).map { it.id })
    }

    @Test fun sortsImagesAndVideosTogetherBySize() {
        val image = MediaItem(1, false, 500, 1, null, 1, "Camera", "a.jpg")
        val video = MediaItem(2, true, 900, 1, 5000, 1, "Camera", "b.mp4")
        assertEquals(listOf(2L, 1L), listOf(image, video).sortedFor(SortMode.LARGEST).map { it.id })
    }

    @Test fun shuffleIsDeterministicForSameSeed() {
        val items = (1L..20L).map { item(it, it, it) }
        assertEquals(
            items.shuffledWithSeed(42L).map { it.id },
            items.shuffledWithSeed(42L).map { it.id },
        )
    }

    @Test fun shuffleKeepsEveryItem() {
        val items = (1L..20L).map { item(it, it, it) }
        assertEquals(items.map { it.id }.toSet(), items.shuffledWithSeed(7L).map { it.id }.toSet())
    }

    @Test fun groupsIntoAlbumsSortedByTotalSizeDescending() {
        val items = listOf(
            item(1, 100, 1, bucket = 1, bucketName = "Camera"),
            item(2, 50, 1, bucket = 2, bucketName = "Screenshots"),
            item(3, 400, 1, bucket = 2, bucketName = "Screenshots"),
        )
        val albums = items.toAlbums()
        assertEquals(listOf("Screenshots", "Camera"), albums.map { it.name })
        assertEquals(2, albums[0].itemCount)
        assertEquals(450L, albums[0].totalBytes)
    }

    @Test fun emptyListProducesNoAlbums() {
        assertEquals(emptyList<Album>(), emptyList<MediaItem>().toAlbums())
    }
}
