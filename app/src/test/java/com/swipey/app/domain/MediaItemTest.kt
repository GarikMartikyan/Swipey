package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemTest {
    private fun item(
        id: Long,
        size: Long,
        date: Long,
        bucket: Long = 1L,
        bucketName: String = "Camera",
        isVideo: Boolean = false,
    ) = MediaItem(id, isVideo, size, date, null, bucket, bucketName, "f$id.jpg")

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

    // -----------------------------------------------------------------------
    // Album covers
    // -----------------------------------------------------------------------
    //
    // Home shows a thumbnail beside every album, and it has to be a *true* one: the
    // album's own most recent item, not whichever row MediaStore happened to hand back
    // first. These pin the selection rule and its tie-break.

    @Test fun albumCoverIsTheMostRecentItem() {
        val items = listOf(
            item(1, 10, date = 100),
            item(2, 10, date = 300),
            item(3, 10, date = 200),
        )
        assertEquals(2L, items.toAlbums().single().coverId)
    }

    @Test fun albumCoverIgnoresTheOrderItemsArriveIn() {
        val newest = item(2, 10, date = 300)
        val rest = listOf(item(1, 10, date = 100), item(3, 10, date = 200))
        assertEquals((listOf(newest) + rest).toAlbums().single().coverId, (rest + newest).toAlbums().single().coverId)
    }

    /**
     * DATE_ADDED has one-second resolution, so a burst of shots — or a bulk import —
     * routinely lands several rows on the same second. The higher `_ID` wins: MediaStore
     * hands out ids monotonically, so within one timestamp the larger id is the later
     * insertion, and the rule is total rather than "whichever the grouping saw last".
     */
    @Test fun albumCoverTieBreaksOnTheHighestId() {
        val items = listOf(
            item(7, 10, date = 300),
            item(9, 10, date = 300),
            item(8, 10, date = 300),
        )
        assertEquals(9L, items.toAlbums().single().coverId)
    }

    @Test fun albumCoverCarriesWhetherItIsAVideo() {
        val items = listOf(
            item(1, 10, date = 100),
            item(2, 10, date = 300, isVideo = true),
        )
        val album = items.toAlbums().single()
        assertEquals(2L, album.coverId)
        assertEquals(true, album.coverIsVideo)
    }

    /**
     * Home's hero is the most recent item on the device, and an album's cover is the most
     * recent item *in that bucket* — the same question asked of two different lists, so
     * they must not be able to disagree about what "most recent" means.
     */
    @Test fun mostRecentUsesTheSameRuleAcrossEveryBucket() {
        val items = listOf(
            item(1, 10, date = 100, bucket = 1),
            item(5, 10, date = 300, bucket = 2),
            item(6, 10, date = 300, bucket = 3),
        )
        assertEquals(6L, items.mostRecent()?.id)
        assertEquals(6L, items.toAlbums().first { it.bucketId == 3L }.coverId)
    }

    @Test fun mostRecentOfNothingIsNull() {
        assertEquals(null, emptyList<MediaItem>().mostRecent())
    }

    @Test fun eachAlbumGetsItsOwnCover() {
        val items = listOf(
            item(1, 100, date = 500, bucket = 1, bucketName = "Camera"),
            item(2, 100, date = 900, bucket = 2, bucketName = "Screenshots"),
            item(3, 400, date = 100, bucket = 2, bucketName = "Screenshots"),
        )
        // Biggest-first, so Screenshots (500 bytes) leads Camera (100).
        assertEquals(listOf(2L, 1L), items.toAlbums().map { it.coverId })
    }

    // --- megapixels: what the info sheet states about a photograph's dimensions ---

    @Test fun megapixelsMultipliesTheTwoDimensions() {
        val item = item(1, 10, 100).copy(widthPx = 4032, heightPx = 3024)
        // 4032 * 3024 = 12,192,768 px.
        assertEquals(12.192768, item.megapixels()!!, 1e-9)
    }

    @Test fun megapixelsIsNullWhenEitherDimensionIsMissing() {
        val base = item(1, 10, 100)
        assertNull(base.megapixels())
        assertNull(base.copy(widthPx = 4032).megapixels())
        assertNull(base.copy(heightPx = 3024).megapixels())
    }

    @Test fun megapixelsIgnoresOrientation() {
        val landscape = item(1, 10, 100).copy(widthPx = 4032, heightPx = 3024)
        val portrait = item(1, 10, 100).copy(widthPx = 3024, heightPx = 4032)
        assertEquals(landscape.megapixels()!!, portrait.megapixels()!!, 1e-9)
    }
}
