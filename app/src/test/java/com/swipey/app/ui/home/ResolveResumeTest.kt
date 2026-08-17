package com.swipey.app.ui.home

import com.swipey.app.data.ResumePoint
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Recent tile's resolution, which is where the bookmark meets the library.
 *
 * The case that matters is the one the tile is named for: the user marked the photograph
 * they stopped on and committed it, so by the time Home reads the bookmark its subject is
 * already in the system trash and out of `queryAll`.
 */
class ResolveResumeTest {

    private fun item(id: Long, date: Long, size: Long = id * 10, bucket: Long = 1L) =
        MediaItem(id, false, size, date, null, bucket, "Camera", "f$id.jpg")

    /** Camera, newest first: 5, 4, 3, 2, 1. */
    private val camera = (1L..5L).map { item(it, date = it * 100) }

    private fun bookmark(
        of: MediaItem,
        bucketId: Long? = null,
        sort: SortMode = SortMode.NEWEST,
        shuffle: Boolean = false,
    ) = ResumePoint(
        itemId = of.id,
        bucketId = bucketId,
        sort = sort.name,
        shuffle = shuffle,
        seed = 0L,
        itemDateSec = of.dateAddedSec,
        itemSizeBytes = of.sizeBytes,
    )

    @Test fun aLivingBookmarkIsOfferedAsItself() {
        val offer = resolveResume(bookmark(camera[2]), camera)
        assertEquals(3L, offer?.item?.id)
        assertEquals(3L, offer?.point?.itemId)
    }

    @Test fun deletingThePhotographYouStoppedOnStillLeavesAnOffer() {
        // The bug: this used to come back null and the tile went dark.
        val offer = resolveResume(bookmark(camera[2]), camera.filterNot { it.id == 3L })
        assertEquals(2L, offer?.item?.id)
        // Dealt after 4, which is the position 3 held.
        assertEquals(4L, offer?.point?.itemId)
    }

    @Test fun deletingTheLastCardFallsBackToTheOneBeforeIt() {
        val offer = resolveResume(bookmark(camera[0]), camera.filterNot { it.id == 1L })
        assertEquals(2L, offer?.item?.id)
        assertEquals(2L, offer?.point?.itemId)
    }

    @Test fun deletingTheWholeTailFallsBackPastAllOfIt() {
        val offer = resolveResume(bookmark(camera[0]), camera.filterNot { it.id in setOf(1L, 2L, 3L) })
        assertEquals(4L, offer?.item?.id)
        assertEquals(4L, offer?.point?.itemId)
    }

    @Test fun deletingTheFirstCardResumesFromTheTop() {
        // Nothing precedes it, so the id stays dead on purpose: the deck finds no card to
        // deal after and opens at the top, which is the position the bookmark named.
        val remaining = camera.filterNot { it.id == 5L }
        val offer = resolveResume(bookmark(camera[4]), remaining)
        assertEquals(4L, offer?.item?.id)
        assertNull(remaining.firstOrNull { it.id == offer?.point?.itemId })
    }

    @Test fun theNeighbourComesFromTheBookmarkedAlbumOnly() {
        val screenshots = listOf(item(9, date = 250, bucket = 2L), item(8, date = 150, bucket = 2L))
        val library = (camera + screenshots).filterNot { it.id == 3L }
        val offer = resolveResume(bookmark(camera[2], bucketId = 1L), library)
        // 9 and 8 both sit between 4 and 2 by date, and neither is in this queue.
        assertEquals(2L, offer?.item?.id)
        assertEquals(4L, offer?.point?.itemId)
    }

    @Test fun theNeighbourFollowsTheBookmarksOwnSort() {
        val sized = listOf(item(1, date = 900, size = 50), item(2, date = 100, size = 30), item(3, date = 500, size = 10))
        val offer = resolveResume(
            bookmark(sized[1], sort = SortMode.LARGEST),
            sized.filterNot { it.id == 2L },
        )
        assertEquals(3L, offer?.item?.id)
        assertEquals(1L, offer?.point?.itemId)
    }

    @Test fun aShuffleFallsBackByDateRatherThanGoingDark() {
        val offer = resolveResume(
            bookmark(camera[2], shuffle = true),
            camera.filterNot { it.id == 3L },
        )
        assertEquals(2L, offer?.item?.id)
    }

    @Test fun anEmptiedAlbumIsNoOfferAtAll() {
        assertNull(resolveResume(bookmark(camera[2], bucketId = 1L), emptyList()))
    }

    @Test fun anUnreadableSortDoesNotThrow() {
        val offer = resolveResume(
            bookmark(camera[2]).copy(sort = "SIDEWAYS"),
            camera.filterNot { it.id == 3L },
        )
        assertEquals(2L, offer?.item?.id)
    }

    // -- bookmarks written before the place was recorded -----------------------

    @Test fun aLegacyBookmarkIsStillHonouredExactly() {
        val legacy = bookmark(camera[2]).copy(itemDateSec = null, itemSizeBytes = null)
        assertEquals(3L, resolveResume(legacy, camera)?.item?.id)
    }

    @Test fun aLegacyBookmarkWhosePhotographIsGoneHasNothingToMeasure() {
        val legacy = bookmark(camera[2]).copy(itemDateSec = null, itemSizeBytes = null)
        assertNull(resolveResume(legacy, camera.filterNot { it.id == 3L }))
    }
}
