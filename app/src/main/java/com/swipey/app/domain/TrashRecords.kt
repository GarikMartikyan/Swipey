package com.swipey.app.domain

/**
 * PENDING_* states exist because the consent dialog runs in MediaProvider's process
 * and can complete while Swipey is dead. See spec §8.1.
 */
enum class TrashState { PENDING_TRASH, TRASHED, PENDING_RESTORE }

/** What Swipey recorded locally before launching a trash or restore request. */
data class LocalTrashRecord(
    val mediaId: Long,
    val isVideo: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val state: TrashState,
)

/** What MediaStore actually reports right now, read with MATCH_INCLUDE. */
data class LiveTrashRow(
    val mediaId: Long,
    val isTrashed: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val dateExpiresSec: Long?,
)
