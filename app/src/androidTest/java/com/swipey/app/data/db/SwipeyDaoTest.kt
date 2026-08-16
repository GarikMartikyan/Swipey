package com.swipey.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swipey.app.domain.TrashState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeyDaoTest {
    private lateinit var db: SwipeyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SwipeyDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun keptIdsAreReturned() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().upsert(ReviewedMediaEntity(2L, "TRASHED", 100L))
        assertEquals(listOf(1L), db.reviewed().keptIds())
    }

    @Test fun deletingReviewedRowMakesItEligibleAgain() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().delete(1L)
        assertEquals(emptyList<Long>(), db.reviewed().keptIds())
    }

    @Test fun resetClearsAllReviewedRows() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().upsert(ReviewedMediaEntity(2L, "KEEP", 100L))
        db.reviewed().clear()
        assertEquals(emptyList<Long>(), db.reviewed().keptIds())
    }

    @Test fun trashedItemRoundTrips() = runBlocking {
        val entity = TrashedItemEntity(5L, false, "a.jpg", 900L, 1_000L, "PENDING_TRASH")
        db.trashed().upsertAll(listOf(entity))
        val loaded = db.trashed().all().single()
        assertEquals(5L, loaded.mediaId)
        assertEquals(TrashState.PENDING_TRASH, loaded.toDomain().state)
    }

    @Test fun pendingRowsAreQueryableSeparately() = runBlocking {
        db.trashed().upsertAll(listOf(
            TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "PENDING_TRASH"),
            TrashedItemEntity(2L, false, "b.jpg", 1L, 1L, "TRASHED"),
            TrashedItemEntity(3L, false, "c.jpg", 1L, 1L, "PENDING_RESTORE"),
        ))
        assertEquals(setOf(1L, 3L), db.trashed().pending().map { it.mediaId }.toSet())
    }

    @Test fun setStateUpdatesInPlace() = runBlocking {
        db.trashed().upsertAll(listOf(TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "PENDING_TRASH")))
        db.trashed().setState(listOf(1L), "TRASHED")
        assertEquals("TRASHED", db.trashed().all().single().state)
    }

    @Test fun deleteRemovesTrashedRows() = runBlocking {
        db.trashed().upsertAll(listOf(TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "TRASHED")))
        db.trashed().delete(listOf(1L))
        assertNull(db.trashed().all().firstOrNull())
    }
}
