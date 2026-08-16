package com.swipey.app.verify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.TrashRepository
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.data.db.TrashedItemEntity
import com.swipey.app.domain.resolveMediaAccess
import com.swipey.app.ui.permission.currentMediaAccess
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 21, partial-access verification without driving the UI.
 *
 * The device this ran on was in active use by its owner, so the permission gate could not be
 * observed on screen. This measures the layer underneath it instead: what the *runtime*
 * permission state on this Samsung build resolves to, and — the part that actually protects
 * the user's data — whether [TrashRepository.verifyAndResolve] refuses to run under anything
 * short of FULL. `PermissionGate` renders `Copy.PARTIAL_*` for PARTIAL and never calls
 * `content()`, so the resolved value fully determines whether the app blocks.
 *
 * Run once per permission configuration, set from adb; it reports whatever it finds rather
 * than asserting a particular state, so a single class covers all three.
 */
@RunWith(AndroidJUnit4::class)
class MediaAccessStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun reportResolvedMediaAccess() {
        fun granted(permission: String) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
        val video = granted(Manifest.permission.READ_MEDIA_VIDEO)
        val userSelected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        Log.i(TAG, "[access] IMAGES=$images VIDEO=$video USER_SELECTED=$userSelected")
        Log.i(TAG, "[access] domain.resolveMediaAccess=${resolveMediaAccess(images, video, userSelected)}")
        Log.i(TAG, "[access] ui.currentMediaAccess=${currentMediaAccess(context)}")

        // The I4 guard. A local record is seeded pointing at an id that does not exist in
        // MediaStore, which is the shape most dangerous to get wrong: under FULL it should
        // resolve to Vanished and be dropped, but under PARTIAL/DENIED the guard must return
        // before querying at all and leave the row untouched — otherwise a permission change
        // would silently delete the only pointer back to files still sitting in the trash.
        // Seeding matters: an empty table short-circuits before the guard is even reached.
        val db = Room.inMemoryDatabaseBuilder(context, SwipeyDatabase::class.java).build()
        runBlocking {
            db.trashed().upsertAll(
                listOf(TrashedItemEntity(ABSENT_MEDIA_ID, false, "gone.jpg", 123L, 1L, "TRASHED")),
            )
        }
        val repo = TrashRepository(context, context.contentResolver, MediaRepository(context.contentResolver), db)
        val report = runBlocking { repo.verifyAndResolve() }
        val survived = runBlocking { db.trashed().all() }.map { it.mediaId }
        Log.i(TAG, "[access] verifyAndResolve isEmpty=${report.isEmpty} report=$report")
        Log.i(TAG, "[access] seeded record survived=${survived.contains(ABSENT_MEDIA_ID)} rows=$survived")
        db.close()

        // What the gate is protecting against: prove the raw trashed query really is
        // unavailable in this state, which is why PARTIAL has to block rather than degrade.
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val probe = runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.MediaColumns._ID),
                args,
                null,
            )?.use { it.count }
        }
        Log.i(TAG, "[access] MATCH_ONLY trashed query -> " +
            probe.fold({ "count=$it" }, { "THREW ${it::class.java.simpleName}: ${it.message}" }))
    }

    private companion object {
        const val TAG = "SwipeyVerify"

        /** Far outside any real MediaStore id, so the live lookup is guaranteed to miss. */
        const val ABSENT_MEDIA_ID = 999_999_999L
    }
}
