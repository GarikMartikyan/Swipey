package com.swipey.app.verify

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.ByteArrayOutputStream

/**
 * Task 21 addendum: fixture for the Sequence A deck-regression check (stale `DeckViewModel`
 * state bouncing a fresh album straight to Review).
 *
 * Sequence A needs two albums that can be swiped end-to-end in a few gestures. Running it on
 * the phone owner's real albums would mean swiping across hundreds of their photos and marking
 * some for the bin, so instead this plants two small albums of generated images — every file
 * created, owned and later deleted by this test. The regression is about navigation state, not
 * about pixel content, so synthetic images exercise it exactly as real ones would.
 *
 * Each image is labelled with its album letter and index so a screenshot proves *which* album's
 * deck is on screen — which is precisely what the PASS/FAIL for Sequence A turns on.
 *
 * Method order is pinned for the same reason as [OemBinProbeTest]: "plant" sorts before "sweep",
 * so a whole-suite run leaves the device clean even though there is no `@After`.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SequenceAFixtureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val collection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @Test fun plantAlbums() {
        ALBUMS.forEach { album ->
            repeat(ITEMS_PER_ALBUM) { index ->
                val uri = createImage(album, index)
                Log.i(TAG, "[seqA] planted $album #$index -> $uri")
            }
        }
        Log.i(TAG, "[seqA] planted ${ALBUMS.size} albums x $ITEMS_PER_ALBUM items")
    }

    @Test fun sweepAlbums() {
        var swept = 0
        ALBUMS.forEach { album ->
            val path = relPath(album)
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                )
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(path))
            }
            val doomed = mutableListOf<Uri>()
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                args,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) != context.packageName) {
                        Log.i(TAG, "[seqA] REFUSING non-owned row id=${c.getLong(0)} owner=${c.getString(1)}")
                        continue
                    }
                    doomed += ContentUris.withAppendedId(collection, c.getLong(0))
                }
            }
            doomed.forEach { uri ->
                val include = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
                // Untrash first: Sequence A marks items for the bin, and a run that got as far
                // as confirming the system dialog would leave them trashed.
                runCatching {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) },
                        include,
                    )
                }
                runCatching { resolver.delete(uri, include) }
                swept++
            }
        }
        Log.i(TAG, "[seqA] swept $swept fixture row(s)")
    }

    private fun createImage(album: String, index: Int): Uri {
        val name = "SWIPEY_${album}_${index}_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath(album))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
        assertNotNull("insert returned null for $name", uri)
        resolver.openOutputStream(uri!!)!!.use { it.write(labelledJpeg(album, index)) }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
        )
        return uri
    }

    /** A big, unmistakable "ALBUM-A 0" so a screenshot identifies the deck without ambiguity. */
    private fun labelledJpeg(album: String, index: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (album == "ALBUMA") Color.rgb(20, 90, 190) else Color.rgb(190, 60, 20))
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 150f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(album, 540f, 660f, paint)
        canvas.drawText("item $index", 540f, 840f, paint)
        return ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            .toByteArray()
    }

    private fun relPath(album: String) = "Pictures/$album/"

    private companion object {
        const val TAG = "SwipeyVerify"
        val ALBUMS = listOf("ALBUMA", "ALBUMB")
        const val ITEMS_PER_ALBUM = 3
    }
}
