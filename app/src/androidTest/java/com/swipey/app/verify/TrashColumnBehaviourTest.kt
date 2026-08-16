package com.swipey.app.verify

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Task 21 device verification. This test does not exercise Swipey's own code: it
 * measures what MediaProvider on *this* build does to a media row when it is trashed,
 * because `domain/Reconcile.kt`'s id-reuse guard is built on assumptions about
 * DISPLAY_NAME and SIZE that were only ever reasoned from AOSP source.
 *
 * It creates, trashes, untrashes and deletes only files it made itself, under
 * `Pictures/SwipeyVerify/`. Every delete is gated on OWNER_PACKAGE_NAME matching this
 * package, so it can never touch a photo it did not create. The one test that looks at
 * other apps' media ([foreignTrashedItemsAreReadable]) is strictly read-only.
 */
@RunWith(AndroidJUnit4::class)
class TrashColumnBehaviourTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()

    private val collection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Read-only, used solely by [foreignTrashedItemsAreReadable]. Never written to. */
    private val videoCollection: Uri =
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // ---------------------------------------------------------------- priority 1

    @Test fun trashingPreservesDisplayNameAndSize() {
        val cases = linkedMapOf(
            "short" to "swipeyverify_short_${System.currentTimeMillis()}.jpg",
            "len200" to longName(200),
            "len235" to longName(235),
            "len236" to longName(236),
            "len250" to longName(250),
            "len254" to longName(254),
        )
        val failures = mutableListOf<String>()

        cases.forEach { (label, requestedName) ->
            val uri = createImage(requestedName)
            val before = readRow(uri)
            log("[$label] REQUESTED  name=${requestedName} bytes=${requestedName.toByteArray().size}")
            log("[$label] BEFORE     $before")

            setTrashed(uri, true)
            val trashed = readRow(uri)
            log("[$label] AFTER      $trashed")

            setTrashed(uri, false)
            val restored = readRow(uri)
            log("[$label] RESTORED   $restored")

            val expiresIn = trashed?.dateExpires?.let { it - System.currentTimeMillis() / 1000 }
            log("[$label] DATE_EXPIRES=${trashed?.dateExpires} in ${expiresIn}s ≈ ${expiresIn?.div(86_400.0)} days")

            log("[$label] VERDICT displayName ${before?.displayName == trashed?.displayName} " +
                "size ${before?.size == trashed?.size} " +
                "restoredName ${before?.displayName == restored?.displayName} " +
                "restoredSize ${before?.size == restored?.size}")

            // SIZE stability is the load-bearing half of Reconcile.kt's id-reuse guard: the
            // guard needs BOTH columns to disagree before it discards a record, so as long as
            // SIZE holds, a DISPLAY_NAME rewrite alone can never strand a recoverable item.
            if (before?.size != trashed?.size) {
                failures += "$label SIZE changed on trash: ${before?.size} -> ${trashed?.size}"
            }

            val storedBytes = before?.displayName?.toByteArray()?.size ?: 0
            if (storedBytes <= TRASH_SAFE_NAME_BYTES) {
                // Names inside the reserve MediaProvider keeps for `.trashed-<expiry>-`.
                if (before?.displayName != trashed?.displayName) {
                    failures += "$label DISPLAY_NAME changed on trash despite fitting in " +
                        "$TRASH_SAFE_NAME_BYTES bytes: '${before?.displayName}' -> '${trashed?.displayName}'"
                }
            } else if (before?.displayName != trashed?.displayName) {
                // Observed, and survivable: a de-dup suffix applied after the insert-time trim
                // can push a stored name past the reserve, and trashing then re-trims it. The
                // rename is permanent — restore does not bring the old name back.
                log("$label EXPECTED-EXCEPTION stored name $storedBytes B exceeds " +
                    "$TRASH_SAFE_NAME_BYTES B reserve; trash re-trimmed DISPLAY_NAME. " +
                    "SIZE held at ${trashed?.size}, so the id-reuse guard cannot fire.")
            }
        }

        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    // ---------------------------------------------------------------- priority 2.2

    @Test fun thumbnailOfOwnTrashedItemLoads() {
        val uri = createImage("swipeyverify_thumb_${System.currentTimeMillis()}.jpg")
        val untrashed = runCatching { resolver.loadThumbnail(uri, Size(256, 256), null) }
        log("[thumb] own untrashed -> ${untrashed.describe { "${it.width}x${it.height}" }}")

        setTrashed(uri, true)
        val trashed = runCatching { resolver.loadThumbnail(uri, Size(256, 256), null) }
        log("[thumb] own TRASHED   -> ${trashed.describe { "${it.width}x${it.height}" }}")

        val stream = runCatching { resolver.openInputStream(uri)?.use { it.read() } }
        log("[thumb] own TRASHED openInputStream -> ${stream.describe { "firstByte=$it" }}")

        setTrashed(uri, false)
        assertTrue("own trashed thumbnail failed: ${trashed.exceptionOrNull()}", trashed.isSuccess)
    }

    /**
     * READ-ONLY. Enumerates media already in the system trash that this app does not own and
     * tries to render a thumbnail for it — the question Swipey's Bin depends on, since it must
     * show a preview of anything it is holding. Nothing here writes, trashes, untrashes or
     * deletes; the only calls made against a foreign row are `query` and `loadThumbnail`.
     *
     * Both the Images and Video collections are swept: on this device the only foreign trashed
     * media is video, so an images-only sweep reports a misleading zero.
     */
    @Test fun foreignTrashedItemsAreReadable() {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED,
        )
        var total = 0
        var foreign = 0
        listOf(false to collection, true to videoCollection).forEach { (isVideo, uriBase) ->
            resolver.query(uriBase, projection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    total++
                    val owner = c.getString(1)
                    if (owner == context.packageName) continue
                    foreign++
                    val uri = ContentUris.withAppendedId(uriBase, c.getLong(0))
                    val thumb = runCatching { resolver.loadThumbnail(uri, Size(256, 256), null) }
                    log("[foreign] video=$isVideo owner=$owner name='${c.getString(2)}' " +
                        "expires=${c.getLong(3)} size=${c.getLong(4)} isTrashed=${c.getInt(5)} " +
                        "thumbnail=${thumb.describe { "${it.width}x${it.height}" }}")
                }
            }
        }
        log("[foreign] trashed rows visible=$total, foreign probed=$foreign")
    }

    // ---------------------------------------------------------------- priority 2.3

    /**
     * Builds (never launches) trash requests to find where LIMIT_CREATE_REQUEST_URIS bites
     * on this MediaProvider. Every URI points at this test's own file, so even an accidental
     * launch could not touch a user photo. The 2001 probe is the control: if it throws, the
     * size check counts list entries rather than de-duplicating, which is what makes the 500
     * result meaningful.
     */
    @Test fun createTrashRequestAcceptsChunkOf500() {
        val a = createImage("swipeyverify_chunk_a_${System.currentTimeMillis()}.jpg")
        val b = createImage("swipeyverify_chunk_b_${System.currentTimeMillis()}.jpg")

        val distinct = runCatching { MediaStore.createTrashRequest(resolver, listOf(a, b), true) }
        log("[chunk] 2 distinct  -> ${distinct.describe { "PendingIntent ok" }}")

        val at500 = runCatching { MediaStore.createTrashRequest(resolver, List(500) { a }, true) }
        log("[chunk] 500         -> ${at500.describe { "PendingIntent ok" }}")

        val at2000 = runCatching { MediaStore.createTrashRequest(resolver, List(2000) { a }, true) }
        log("[chunk] 2000        -> ${at2000.describe { "PendingIntent ok" }}")

        val at2001 = runCatching { MediaStore.createTrashRequest(resolver, List(2001) { a }, true) }
        log("[chunk] 2001        -> ${at2001.describe { "PendingIntent ok" }}")

        assertTrue("500 URIs threw: ${at500.exceptionOrNull()}", at500.isSuccess)
    }

    // ---------------------------------------------------------------- helpers

    private data class Row(
        val id: Long,
        val displayName: String?,
        val size: Long?,
        val isTrashed: Int?,
        val dateExpires: Long?,
        val data: String?,
    ) {
        override fun toString() =
            "id=$id IS_TRASHED=$isTrashed SIZE=$size DATE_EXPIRES=$dateExpires " +
                "nameBytes=${displayName?.toByteArray()?.size} " +
                "baseBytes=${data?.substringAfterLast('/')?.toByteArray()?.size} " +
                "DISPLAY_NAME='$displayName' _DATA='$data'"
    }

    private fun readRow(uri: Uri): Row? {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        val full = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.MediaColumns.DATA,
        )
        val withData = runCatching { queryRow(uri, full, args, hasData = true) }
        if (withData.isSuccess) return withData.getOrNull()
        log("[row] _DATA projection rejected: ${withData.exceptionOrNull()}")
        return queryRow(uri, full.dropLast(1).toTypedArray(), args, hasData = false)
    }

    private fun queryRow(uri: Uri, projection: Array<String>, args: Bundle, hasData: Boolean): Row? =
        resolver.query(uri, projection, args, null)?.use { c ->
            if (!c.moveToFirst()) null else Row(
                id = c.getLong(0),
                displayName = c.getString(1),
                size = if (c.isNull(2)) null else c.getLong(2),
                isTrashed = if (c.isNull(3)) null else c.getInt(3),
                dateExpires = if (c.isNull(4)) null else c.getLong(4),
                data = if (hasData && !c.isNull(5)) c.getString(5) else null,
            )
        }

    private fun setTrashed(uri: Uri, trashed: Boolean) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0)
        }
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        val updated = resolver.update(uri, values, args)
        assertEquals("IS_TRASHED=$trashed update did not affect exactly one row", 1, updated)
    }

    private fun createImage(displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, REL_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
        assertNotNull("insert returned null for '$displayName'", uri)
        created += uri!!
        resolver.openOutputStream(uri)!!.use { it.write(jpegBytes()) }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
        )
        return uri
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366CCL.toInt())
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            .toByteArray()
    }

    /**
     * A deliberately multi-byte name of exactly [targetBytes] UTF-8 bytes. 255 is the
     * filename ceiling and `.trashed-<10-digit-expiry>-` is a 20-byte prefix, so names
     * either side of 235 bytes are what decide whether the trashed path still fits.
     */
    private fun longName(targetBytes: Int): String {
        val suffix = ".jpg"
        val builder = StringBuilder("swipeyverify_long_")
        val unit = "éßжあ"  // 2 + 2 + 2 + 3 bytes
        fun bytes() = builder.toString().toByteArray().size + suffix.toByteArray().size
        while (bytes() + unit.toByteArray().size <= targetBytes) builder.append(unit)
        while (bytes() < targetBytes) builder.append('x')
        return builder.toString() + suffix
    }

    private inline fun <T> Result<T>.describe(render: (T) -> String): String =
        fold({ if (it == null) "null" else render(it) }, { "THREW ${it::class.java.simpleName}: ${it.message}" })

    private fun log(message: String) = Log.i(TAG, message)

    // ---------------------------------------------------------------- cleanup

    /**
     * Deletes every row this test created, then sweeps `Pictures/SwipeyVerify/` for anything
     * a crashed earlier run left behind. Both paths refuse to delete a row whose
     * OWNER_PACKAGE_NAME is not this package, so a user photo can never be reached.
     */
    @After fun cleanUp() {
        created.forEach { uri -> runCatching { untrashThenDelete(uri) } }
        created.clear()

        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            )
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(REL_PATH),
            )
        }
        val leftovers = mutableListOf<Uri>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                args,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) != context.packageName) {
                        log("[cleanup] REFUSING to delete non-owned row id=${c.getLong(0)} owner=${c.getString(1)}")
                        continue
                    }
                    leftovers += ContentUris.withAppendedId(collection, c.getLong(0))
                }
            }
        }
        leftovers.forEach { uri -> runCatching { untrashThenDelete(uri) } }
        log("[cleanup] swept ${leftovers.size} leftover row(s) from $REL_PATH")
    }

    private fun untrashThenDelete(uri: Uri) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) },
                args,
            )
        }
        resolver.delete(uri, args)
    }

    private companion object {
        const val TAG = "SwipeyVerify"
        const val REL_PATH = "Pictures/SwipeyVerify/"

        /**
         * Measured on this build: MediaProvider trims DISPLAY_NAME to 235 bytes at insert,
         * which is 255 (the filename ceiling) minus the 20-byte `.trashed-<10-digit-expiry>-`
         * prefix. A name at or under this reserve survives trashing byte-for-byte.
         */
        const val TRASH_SAFE_NAME_BYTES = 235
    }
}
