package com.swipey.app.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.provider.MediaStore
import com.swipey.app.data.db.ReviewedMediaEntity
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.data.db.TrashedItemEntity
import com.swipey.app.data.db.toDomain
import com.swipey.app.domain.BinView
import com.swipey.app.domain.LocalTrashRecord
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.Resolution
import com.swipey.app.domain.TrashState
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.reconcileBin
import com.swipey.app.domain.resolveRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a recovery or verification pass actually changed, for honest per-item reporting. */
data class RecoveryReport(
    val confirmedTrashed: List<Long>,
    val restored: List<Long>,
    val declined: List<Long>,
    val vanished: List<Long>,
) {
    val isEmpty: Boolean get() =
        confirmedTrashed.isEmpty() && restored.isEmpty() && declined.isEmpty() && vanished.isEmpty()
}

class TrashRepository(
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
     * The spec §8.1 recovery pass. Safe to call at any time — on app start, on Bin
     * open, and after every consent dialog result. RESULT_OK is never trusted;
     * this is what decides what actually happened.
     */
    suspend fun verifyAndResolve(): RecoveryReport = withContext(Dispatchers.IO) {
        val local = db.trashed().all().map { it.toDomain() }
        if (local.isEmpty()) return@withContext RecoveryReport(emptyList(), emptyList(), emptyList(), emptyList())

        val live = liveRowsFor(local)
        val resolutions = resolveRecords(local, live)

        val confirmed = resolutions.filterIsInstance<Resolution.MarkTrashed>().map { it.mediaId }
        val declined = resolutions.filterIsInstance<Resolution.DeleteRecord>().map { it.mediaId }
        val restored = resolutions.filterIsInstance<Resolution.DeleteRecordAndReview>().map { it.mediaId }
        val vanished = resolutions.filterIsInstance<Resolution.Vanished>().map { it.mediaId }

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

        RecoveryReport(confirmed, restored, declined, vanished)
    }

    suspend fun binView(): BinView = withContext(Dispatchers.IO) {
        val local = db.trashed().all().map { it.toDomain() }
        reconcileBin(local, liveRowsFor(local))
    }

    suspend fun trashedCount(): Int = withContext(Dispatchers.IO) { db.trashed().trashedCount() }

    private suspend fun liveRowsFor(local: List<LocalTrashRecord>) =
        media.verify(local.filter { !it.isVideo }.map { it.mediaId }, isVideo = false) +
            media.verify(local.filter { it.isVideo }.map { it.mediaId }, isVideo = true)
}
