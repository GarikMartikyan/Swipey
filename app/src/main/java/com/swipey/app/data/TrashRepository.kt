package com.swipey.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.swipey.app.data.db.ReviewedMediaEntity
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.data.db.TrashedItemEntity
import com.swipey.app.data.db.toDomain
import com.swipey.app.domain.BinView
import com.swipey.app.domain.LiveTrashRow
import com.swipey.app.domain.LocalTrashRecord
import com.swipey.app.domain.MediaAccess
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.Resolution
import com.swipey.app.domain.TrashState
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.reconcileBin
import com.swipey.app.domain.resolveMediaAccess
import com.swipey.app.domain.resolveRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Serializable

/**
 * What a recovery or verification pass actually changed, for honest per-item reporting.
 *
 * Fix round 2, Important 3: [Serializable] so `SwipeyApp.kt`'s Result-route state can be
 * held in `rememberSaveable` rather than plain `remember` — every field is a `List<Long>`,
 * so this is a trivial, lossless round trip through a `Bundle`.
 */
data class RecoveryReport(
    val confirmedTrashed: List<Long>,
    val restored: List<Long>,
    val declined: List<Long>,
    val vanished: List<Long>,
    /**
     * Whole-branch review, I2: rows still inside the PENDING_TRASH grace window — asked
     * for, not yet confirmed either way. Deliberately separate from [declined]: nothing
     * was written for these, and a caller must not report them as an outcome. They do
     * count towards "how many did this commit attempt", which is why ResultScreen adds
     * them to its denominator (spec §9 rule 6).
     */
    val awaiting: List<Long> = emptyList(),
) : Serializable {
    val isEmpty: Boolean get() =
        confirmedTrashed.isEmpty() && restored.isEmpty() && declined.isEmpty() &&
            vanished.isEmpty() && awaiting.isEmpty()
}

class TrashRepository(
    private val context: Context,
    private val resolver: ContentResolver,
    private val media: MediaRepository,
    private val db: SwipeyDatabase,
) {

    /**
     * Written BEFORE the PendingIntent is launched. The consent dialog runs in
     * MediaProvider's process and can complete while Swipey is dead; without this
     * record the items would be trashed with nothing pointing at them. Spec §8.1.
     */
    suspend fun markPendingTrash(items: List<MediaItem>, now: Long) = withContext(Dispatchers.IO) {
        db.trashed().upsertAll(
            items.map {
                TrashedItemEntity(
                    mediaId = it.id,
                    isVideo = it.isVideo,
                    displayName = it.displayName,
                    sizeBytes = it.sizeBytes,
                    trashedAt = now,
                    state = TrashState.PENDING_TRASH.name,
                )
            },
        )
    }

    suspend fun markPendingRestore(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.trashed().setState(ids, TrashState.PENDING_RESTORE.name)
    }

    /** One consent dialog per chunk. Spec §8. */
    fun buildTrashRequests(items: List<MediaItem>): List<PendingIntent> =
        items.chunkedForRequest().map { chunk ->
            MediaStore.createTrashRequest(resolver, chunk.map { it.contentUri() }, true)
        }

    fun buildRestoreRequests(records: List<LocalTrashRecord>): List<PendingIntent> =
        records.chunkedForRequest().map { chunk ->
            MediaStore.createTrashRequest(
                resolver,
                chunk.map { contentUriFor(it.mediaId, it.isVideo) },
                false,
            )
        }

    /**
     * The one irreversible thing the app can ask for: emptying items out of the trash for
     * good.
     *
     * Deliberately has no `prepareDelete` twin, and writes no PENDING_ row — which is the
     * one place this direction is *simpler* than the other two rather than more dangerous.
     * The bookkeeping exists to answer "did the thing I asked for actually happen", and for
     * trash and restore that question is genuinely hard: the row survives either way and
     * only a flag distinguishes the outcomes. A deletion answers itself. The row is gone, so
     * [verifyAndResolve] finds no live media for the record, resolves it to
     * [Resolution.Vanished] and drops it — exactly the path it already takes for an item
     * deleted from Google Photos, because from this app's side those two events are the same
     * event. A cancelled dialog leaves the row untouched and the record stays trashed.
     *
     * MediaStore shows its own confirmation, and it is the only confirmation there is:
     * nothing here can be undone afterwards, by this app or any other.
     */
    fun buildDeleteRequests(records: List<LocalTrashRecord>): List<PendingIntent> =
        records.chunkedForRequest().map { chunk ->
            MediaStore.createDeleteRequest(
                resolver,
                chunk.map { contentUriFor(it.mediaId, it.isVideo) },
            )
        }

    /**
     * I1: collapses markPendingTrash + buildTrashRequests into one suspend call so the
     * PendingIntent cannot be obtained before the durable row is committed. A caller that
     * calls the two steps separately from a non-coroutine call site (e.g. a Compose
     * onClick) has no compiler-enforced ordering between them — this does.
     */
    suspend fun prepareTrash(items: List<MediaItem>, now: Long): List<PendingIntent> {
        markPendingTrash(items, now)
        return buildTrashRequests(items)
    }

    /** Same guarantee as [prepareTrash], for the restore direction. */
    suspend fun prepareRestore(records: List<LocalTrashRecord>): List<PendingIntent> {
        markPendingRestore(records.map { it.mediaId })
        return buildRestoreRequests(records)
    }

    /**
     * The spec §8.1 recovery pass. Safe to call at any time the app currently holds full
     * media access — on app start, on Bin open, and after every consent dialog result.
     * RESULT_OK is never trusted; this is what decides what actually happened.
     *
     * Precondition, not a blanket guarantee: without READ_MEDIA_IMAGES + READ_MEDIA_VIDEO
     * (e.g. permission revoked in Settings after items were trashed, then Swipey relaunched)
     * MediaProvider throws SecurityException on the verification query. Without a guard,
     * every local record would have no live row to match, resolve to Vanished, and be
     * deleted — destroying the only pointer back to files that are still sitting in the
     * system trash. So this returns an untouched, empty report instead of querying at all
     * when access isn't FULL. Spec §8.1, review finding I4.
     */
    suspend fun verifyAndResolve(): RecoveryReport = withContext(Dispatchers.IO) {
        if (!hasFullMediaAccess()) return@withContext EMPTY_RECOVERY_REPORT

        val local = db.trashed().all().map { it.toDomain() }
        if (local.isEmpty()) return@withContext EMPTY_RECOVERY_REPORT

        val live = liveRowsFor(local)
        val resolutions = resolveRecords(local, live, System.currentTimeMillis())

        val confirmed = resolutions.filterIsInstance<Resolution.MarkTrashed>().map { it.mediaId }
        val declined = resolutions.filterIsInstance<Resolution.DeleteRecord>().map { it.mediaId }
        val restored = resolutions.filterIsInstance<Resolution.DeleteRecordAndReview>().map { it.mediaId }
        val vanished = resolutions.filterIsInstance<Resolution.Vanished>().map { it.mediaId }
        // I2: reported, never written. AwaitingConsent maps to no DB call at all — that is
        // the entire point of the state.
        val awaiting = resolutions.filterIsInstance<Resolution.AwaitingConsent>().map { it.mediaId }

        if (confirmed.isNotEmpty()) {
            db.trashed().setState(confirmed, TrashState.TRASHED.name)
            db.reviewed().upsertAll(
                confirmed.map { ReviewedMediaEntity(it, "TRASHED", System.currentTimeMillis()) },
            )
        }
        if (declined.isNotEmpty()) db.trashed().delete(declined)
        if (restored.isNotEmpty()) {
            db.trashed().delete(restored)
            db.reviewed().deleteAll(restored)   // restored items become swipeable again (spec §8)
        }
        if (vanished.isNotEmpty()) db.trashed().delete(vanished)

        RecoveryReport(confirmed, restored, declined, vanished, awaiting)
    }

    /** Same I4 exposure as [verifyAndResolve] — [reconcileBin] also queries live rows. */
    suspend fun binView(): BinView = withContext(Dispatchers.IO) {
        if (!hasFullMediaAccess()) return@withContext BinView(emptyList(), emptyList())

        val local = db.trashed().all().map { it.toDomain() }
        reconcileBin(local, liveRowsFor(local), System.currentTimeMillis())
    }

    suspend fun trashedCount(): Int = withContext(Dispatchers.IO) { db.trashed().trashedCount() }

    /**
     * Whole-branch review, I3: every id is looked up in BOTH collections and the results
     * merged, rather than only in the one its locally-stored `isVideo` flag names.
     *
     * The Images and Video collection URIs are views over MediaProvider's single `files`
     * table discriminated by `media_type`. If the stored flag ever disagrees with the
     * row's current `media_type`, a single-collection lookup returns nothing — and an
     * absent row resolves to [Resolution.Vanished], which deletes the local record. That
     * is the one branch with no `isTrashed` protection at all (there is no row to
     * consult), so R20's "a row Swipey holds in trust can never be discarded" guarantee
     * does not reach it, and the outcome is an item sitting in the system trash with no
     * Bin entry.
     *
     * The flag can go stale under us: trashing sets `triggerScan = true` right after the
     * rename and the scanner rewrites columns unconditionally — the same mechanism the W2
     * investigation documented correcting SIZE on FUSE-created rows also recomputes
     * `media_type`. Querying both collections removes the dependence on a stored flag
     * Swipey never refreshes; when the flag is right the extra query simply returns
     * nothing. `Map + Map` is safe here because an id can only exist in one of the two
     * views at a time.
     */
    private suspend fun liveRowsFor(local: List<LocalTrashRecord>): Map<Long, LiveTrashRow> {
        val ids = local.map { it.mediaId }
        return media.verify(ids, isVideo = false) + media.verify(ids, isVideo = true)
    }

    /**
     * Reuses domain.resolveMediaAccess (Task 6) rather than duplicating its FULL/PARTIAL/
     * DENIED logic. Cannot reuse ui.permission.PermissionGate.currentMediaAccess directly:
     * that would make data/ depend on ui/, the wrong direction for this layering, so the
     * Manifest-permission plumbing below is intentionally kept local and small instead.
     */
    private fun hasFullMediaAccess(): Boolean {
        fun granted(permission: String) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return resolveMediaAccess(
            imagesGranted = granted(Manifest.permission.READ_MEDIA_IMAGES),
            videoGranted = granted(Manifest.permission.READ_MEDIA_VIDEO),
            userSelectedGranted = Build.VERSION.SDK_INT >= 34 &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        ) == MediaAccess.FULL
    }

    private companion object {
        val EMPTY_RECOVERY_REPORT = RecoveryReport(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
