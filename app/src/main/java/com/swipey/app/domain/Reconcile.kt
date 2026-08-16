package com.swipey.app.domain

sealed interface Resolution {
    val mediaId: Long
    /** Confirmed trashed — set state to TRASHED. */
    data class MarkTrashed(override val mediaId: Long) : Resolution
    /** Not trashed and never was ours to keep — drop the trash record only. */
    data class DeleteRecord(override val mediaId: Long) : Resolution
    /** Back in the gallery — drop the trash record AND the reviewed row so it is swipeable again. */
    data class DeleteRecordAndReview(override val mediaId: Long) : Resolution
    /** No longer on the device at all. */
    data class Vanished(override val mediaId: Long) : Resolution
    /** Still trashed, still correct — no change. */
    data class Keep(override val mediaId: Long) : Resolution
}

/**
 * The spec §8.1 resolution table, used both by the startup recovery pass and by
 * Bin reconciliation.
 *
 * A live row whose displayName or sizeBytes disagrees with the local record means
 * MediaStore reused the id for a different file — treat it as vanished rather than
 * pointing the Bin at the wrong photo.
 */
fun resolveRecords(
    local: List<LocalTrashRecord>,
    live: Map<Long, LiveTrashRow>,
): List<Resolution> = local.map { record ->
    val row = live[record.mediaId]
    when {
        row == null -> Resolution.Vanished(record.mediaId)
        row.displayName != record.displayName || row.sizeBytes != record.sizeBytes ->
            Resolution.Vanished(record.mediaId)
        row.isTrashed -> when (record.state) {
            TrashState.TRASHED -> Resolution.Keep(record.mediaId)
            TrashState.PENDING_TRASH, TrashState.PENDING_RESTORE ->
                Resolution.MarkTrashed(record.mediaId)
        }
        else -> when (record.state) {
            TrashState.PENDING_TRASH -> Resolution.DeleteRecord(record.mediaId)
            TrashState.TRASHED, TrashState.PENDING_RESTORE ->
                Resolution.DeleteRecordAndReview(record.mediaId)
        }
    }
}

data class BinEntry(val record: LocalTrashRecord, val expiresAtSec: Long?)

data class BinView(val entries: List<BinEntry>, val vanished: List<Long>)

/** What the Bin screen renders: only rows still genuinely trashed, plus what disappeared. */
fun reconcileBin(local: List<LocalTrashRecord>, live: Map<Long, LiveTrashRow>): BinView {
    val resolutions = resolveRecords(local, live).associateBy { it.mediaId }
    val byId = local.associateBy { it.mediaId }
    val entries = mutableListOf<BinEntry>()
    val vanished = mutableListOf<Long>()
    resolutions.values.forEach { resolution ->
        when (resolution) {
            is Resolution.Keep, is Resolution.MarkTrashed -> {
                val record = byId.getValue(resolution.mediaId)
                entries += BinEntry(record, live[resolution.mediaId]?.dateExpiresSec)
            }
            else -> vanished += resolution.mediaId
        }
    }
    return BinView(entries.sortedByDescending { it.record.trashedAt }, vanished)
}
