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

    /**
     * Whole-branch review, I2. A PENDING_TRASH row whose consent dialog is still young
     * enough that it may not have been answered yet. Touch nothing: the row is the only
     * pointer back to a file the user may be about to trash.
     */
    data class AwaitingConsent(override val mediaId: Long) : Resolution
}

/**
 * Whole-branch review, I2: how long a PENDING_TRASH row is held before an untrashed live
 * row is accepted as proof the user declined.
 *
 * The §8.1 table maps PENDING_TRASH + IS_TRASHED = 0 to "delete row (user declined)" with
 * no state for *"launched, not yet answered"* — and those two look identical from here.
 * The consent dialog runs in MediaProvider's process; Swipey can be killed and recreated
 * behind it, run the recovery pass, read IS_TRASHED = 0 because the user simply has not
 * tapped yet, delete the row, and then have the user tap Allow. The file is trashed with
 * no local record: absent from the Bin (only `trashed_by_swipey` rows render there) and
 * absent from the deck (§5.2 excludes trashed items for free). That is verbatim the hole
 * §8.1 opens with.
 *
 * The asymmetry R20 already chose applies exactly: a lingering record is cosmetic, a
 * deleted live one is permanent. An unresolved row costs nothing — [reconcileBin] shows
 * it nowhere, `trashedCount()` counts only `state = 'TRASHED'` — so holding it for a few
 * minutes is free, and the next pass after the window resolves it normally.
 */
const val PENDING_TRASH_GRACE_MILLIS = 5 * 60 * 1000L

/**
 * The spec §8.1 resolution table, used both by the startup recovery pass and by
 * Bin reconciliation.
 *
 * The id-reuse guard below only fires for an *untrashed* live row whose displayName
 * AND sizeBytes both disagree with the local record. It is deliberately narrower than
 * "any mismatch": MediaProvider legitimately deletes and recomputes DISPLAY_NAME on
 * every trash update (trimFilename truncation once the `.trashed-<epoch>-` prefix pushes
 * a long name past 255 bytes, ensureUniqueFileColumns de-dup suffixing, MIME-driven
 * extension coercion), and it unconditionally rewrites SIZE via the post-rename rescan
 * it triggers (correcting a stale or zero SIZE left by an unscanned FUSE write). Both can
 * happen to a row that is genuinely still trashed. Restricting the guard to untrashed rows
 * means a row Swipey is actually holding in trust can never be discarded by it — a reused
 * id points at a newly-scanned file, which in practice is not trashed (a fresh scan carries
 * no `.trashed-` prefix and has had no explicit trash request applied to it), so
 * IS_TRASHED = 1 is itself strong identity evidence. Requiring both columns to disagree
 * (rather than either) means a single-column MediaProvider rewrite is survivable while a
 * real foreign file — which essentially never matches both name and size by chance — still
 * trips it.
 */
fun resolveRecords(
    local: List<LocalTrashRecord>,
    live: Map<Long, LiveTrashRow>,
    now: Long,
    graceMillis: Long = PENDING_TRASH_GRACE_MILLIS,
): List<Resolution> = local.map { record ->
    val row = live[record.mediaId]
    when {
        row == null -> Resolution.Vanished(record.mediaId)
        !row.isTrashed && row.displayName != record.displayName && row.sizeBytes != record.sizeBytes ->
            Resolution.Vanished(record.mediaId)
        row.isTrashed -> when (record.state) {
            TrashState.TRASHED -> Resolution.Keep(record.mediaId)
            TrashState.PENDING_TRASH, TrashState.PENDING_RESTORE ->
                Resolution.MarkTrashed(record.mediaId)
        }
        else -> when (record.state) {
            // I2: only PENDING_TRASH needs the window. A PENDING_RESTORE row whose dialog
            // is still unanswered reads IS_TRASHED = 1 and takes the branch above, so an
            // unanswered restore is already safe; it is specifically "we asked to trash
            // this and MediaStore still says it isn't trashed" that is ambiguous between
            // "declined" and "not answered yet". See PENDING_TRASH_GRACE_MILLIS.
            TrashState.PENDING_TRASH ->
                if (now - record.trashedAt < graceMillis) Resolution.AwaitingConsent(record.mediaId)
                else Resolution.DeleteRecord(record.mediaId)
            TrashState.TRASHED, TrashState.PENDING_RESTORE ->
                Resolution.DeleteRecordAndReview(record.mediaId)
        }
    }
}

data class BinEntry(val record: LocalTrashRecord, val expiresAtSec: Long?)

data class BinView(val entries: List<BinEntry>, val vanished: List<Long>)

/** What the Bin screen renders: only rows still genuinely trashed, plus what disappeared. */
fun reconcileBin(local: List<LocalTrashRecord>, live: Map<Long, LiveTrashRow>, now: Long): BinView {
    val resolutions = resolveRecords(local, live, now).associateBy { it.mediaId }
    val byId = local.associateBy { it.mediaId }
    val entries = mutableListOf<BinEntry>()
    val vanished = mutableListOf<Long>()
    resolutions.values.forEach { resolution ->
        when (resolution) {
            is Resolution.Keep, is Resolution.MarkTrashed -> {
                val record = byId.getValue(resolution.mediaId)
                entries += BinEntry(record, live[resolution.mediaId]?.dateExpiresSec)
            }
            // I2: held, not gone. It is not trashed, so it is not a Bin entry; and it has
            // not been established as declined, so counting it in `vanished` would tell
            // the user "N items are no longer in the trash" about an item they may be
            // about to put there. Neither list — the row is simply invisible until the
            // grace window closes.
            is Resolution.AwaitingConsent -> Unit
            else -> vanished += resolution.mediaId
        }
    }
    return BinView(entries.sortedByDescending { it.record.trashedAt }, vanished)
}
