package com.swipey.app.verify

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
 * Task 21, Samsung OEM question: does Samsung Gallery's own Recycle Bin show an item that
 * was trashed through MediaStore, and does a MediaStore-trashed item leave the Gallery grid?
 *
 * Answering that needs a file that *stays* trashed while a human looks at Gallery, so unlike
 * [TrashColumnBehaviourTest] this class has no `@After` cleanup. The two tests are meant to
 * be run separately: [plantTrashedProbe], then inspect Gallery, then [sweepProbe].
 *
 * Method order is pinned because of that missing cleanup: "plant" sorts before "sweep", so a
 * whole-class or whole-suite run still ends with the device clean. Without the pin JUnit's
 * order is unspecified, and a sweep-then-plant run leaves a trashed file on the user's phone.
 *
 * It only ever writes to `Pictures/SwipeyOemProbe/`, and every delete is gated on
 * OWNER_PACKAGE_NAME matching this package, so it cannot reach a photo it did not create.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OemBinProbeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val collection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @Test fun plantTrashedProbe() {
        val name = "SWIPEY_OEM_PROBE_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, REL_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
        assertNotNull("insert returned null", uri)
        resolver.openOutputStream(uri!!)!!.use { it.write(jpegBytes()) }
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null)
        Log.i(TAG, "[oem] planted untrashed uri=$uri name=$name")

        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 1)
        }, matchIncludeArgs())
        Log.i(TAG, "[oem] TRASHED uri=$uri name=$name — inspect Samsung Gallery now")

        // Prove MediaStore's own default-visibility contract on this build: the row must be
        // absent from a plain collection query and present under MATCH_ONLY.
        Log.i(TAG, "[oem] visible in default query=${countMatching(name, null)}")
        Log.i(TAG, "[oem] visible under MATCH_ONLY=${countMatching(name, MediaStore.MATCH_ONLY)}")
    }

    @Test fun sweepProbe() {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(REL_PATH))
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
                    Log.i(TAG, "[oem] REFUSING non-owned row id=${c.getLong(0)} owner=${c.getString(1)}")
                    continue
                }
                doomed += ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }
        doomed.forEach { uri ->
            runCatching {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                }, matchIncludeArgs())
            }
            runCatching { resolver.delete(uri, matchIncludeArgs()) }
        }
        Log.i(TAG, "[oem] swept ${doomed.size} probe row(s) from $REL_PATH")
    }

    private fun countMatching(name: String, matchTrashed: Int?): Int {
        val args = Bundle().apply {
            matchTrashed?.let { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, it) }
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(name))
        }
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), args, null)
            ?.use { it.count } ?: -1
    }

    private fun matchIncludeArgs() = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFCC2222L.toInt())
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            .toByteArray()
    }

    private companion object {
        const val TAG = "SwipeyVerify"
        const val REL_PATH = "Pictures/SwipeyOemProbe/"
    }
}
