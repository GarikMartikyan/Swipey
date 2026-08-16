package com.swipey.app.verify

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swipey.app.data.MediaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 21: exercises Swipey's own read paths against the real media library on this device
 * (~2.5k items) rather than a fixture. READ-ONLY — [MediaRepository] has no write path.
 *
 * This is where Ruling R3 gets tested for real: MediaProvider validates projections strictly
 * and throws IllegalArgumentException on an unknown column, and DURATION is not valid on the
 * Images collection. A fixture cannot catch a column that this OEM's fork rejects; the live
 * library can. Requires FULL media access.
 */
@RunWith(AndroidJUnit4::class)
class RealMediaQueryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val media = MediaRepository(context.contentResolver)

    @Test fun queryAllSucceedsOnRealLibrary() {
        val elapsed = System.currentTimeMillis()
        val items = runBlocking { media.queryAll() }
        val took = System.currentTimeMillis() - elapsed

        val videos = items.count { it.isVideo }
        Log.i(TAG, "[real] queryAll -> ${items.size} items (${items.size - videos} images, " +
            "$videos videos) in ${took}ms")
        Log.i(TAG, "[real] sized=${items.count { it.sizeBytes > 0 }} " +
            "named=${items.count { !it.displayName.isNullOrBlank() }} " +
            "bucketed=${items.count { it.bucketId != 0L }}")
        Log.i(TAG, "[real] videos with duration=${items.count { it.isVideo && it.durationMs != null }}")
        items.take(3).forEach { Log.i(TAG, "[real] sample $it") }

        assertTrue("queryAll returned nothing on a device with a real library", items.isNotEmpty())
    }

    @Test fun queryTrashedSucceedsOnRealLibrary() {
        val rows = runBlocking { media.queryTrashed() }
        Log.i(TAG, "[real] queryTrashed -> ${rows.size} row(s)")
        rows.values.forEach { Log.i(TAG, "[real] trashed $it") }
    }

    /** The MATCH_INCLUDE verification path, against ids that really exist. */
    @Test fun verifyRoundTripsRealIds() {
        val items = runBlocking { media.queryAll() }.filter { !it.isVideo }.take(5)
        val ids = items.map { it.id }
        val verified = runBlocking { media.verify(ids, isVideo = false) }
        Log.i(TAG, "[real] verify(${ids.size} ids) -> ${verified.size} row(s)")
        verified.values.forEach { Log.i(TAG, "[real] verified $it") }
        assertTrue(
            "verify() lost ids that queryAll had just returned",
            verified.keys.containsAll(ids),
        )
    }

    private companion object {
        const val TAG = "SwipeyVerify"
    }
}
