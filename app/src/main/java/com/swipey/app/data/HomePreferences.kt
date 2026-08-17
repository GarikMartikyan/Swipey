package com.swipey.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * Where the last session got to, and how to open it again.
 *
 * The first five fields are needed to keep the promise "carry on from here": the item says
 * where, and the rest say *which queue that position means*. The same photograph sits at a
 * different place in an album than in the whole library, and somewhere else again in a
 * shuffle — so a resume that remembered only the id would land somewhere arbitrary.
 *
 * @param itemId the last photograph the user decided on. Not the last one they scrolled
 *   past: scrolling is looking, and a resume point that moved when nothing was judged would
 *   quietly skip whatever they had scrolled over.
 * @param bucketId the album it belonged to, or null for the whole library.
 * @param seed only meaningful when [shuffle] is set, and then it is essential — a shuffle
 *   with a different seed is a different queue, and the remembered position would name a
 *   different photograph.
 * @param itemDateSec the photograph's `DATE_ADDED`, and @param itemSizeBytes its size:
 *   together with [itemId] they are its place in a queue's order, which is what lets the
 *   bookmark **outlive the photograph**. The commonest way to end a session is to mark the
 *   photograph you stopped on and commit it, so a bookmark that could only be honoured while
 *   its subject existed went dark exactly when it was most wanted. See
 *   [com.swipey.app.domain.QueuePlace]. Null only for a bookmark written before this was
 *   recorded, which the next decision replaces.
 */
data class ResumePoint(
    val itemId: Long,
    val bucketId: Long?,
    val sort: String,
    val shuffle: Boolean,
    val seed: Long,
    val itemDateSec: Long? = null,
    val itemSizeBytes: Long? = null,
)

/**
 * What Home remembers between launches: whether its Albums section is drawn as rows or as
 * tiles, and where the last swiping session got to.
 *
 * Deliberately [android.content.SharedPreferences] and not a Room table. `SwipeyDatabase`
 * holds records that the app's recoverability guarantee depends on — which photos were
 * kept, which are pending in the system trash — and every one of them is reconciled
 * against MediaStore on launch. A layout toggle and a bookmark have nothing to reconcile
 * and no consequence if they are lost, and putting them there would mean a schema
 * migration, a DAO and a place for a genuinely important query to go wrong.
 *
 * That "no consequence if lost" is worth being explicit about for [resumePoint], because it
 * is written on **every swipe**: hundreds of times a session. `apply()` is what makes that
 * affordable — the value is in memory at once and reaches disk on a background thread — and
 * losing the last few to a kill costs the user a couple of cards of accuracy in an offer
 * they can decline. It is a bookmark, not a record.
 *
 * The read is a blocking disk load on first touch, so callers do it off the main thread —
 * see `HomeViewModel` and `DeckViewModel`.
 */
class HomePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Rows are the default: they carry the size, which is the reason to open an album. */
    var albumsAsGrid: Boolean
        get() = prefs.getBoolean(KEY_ALBUMS_AS_GRID, false)
        set(value) = prefs.edit { putBoolean(KEY_ALBUMS_AS_GRID, value) }

    /**
     * The last decision the user made, and the queue it was made in. Null until they have
     * swiped at all — which is the state Home's Recent tile draws as unavailable rather
     * than as an empty one.
     *
     * Written as separate keys rather than one encoded string so the file stays readable and
     * a half-written value can never parse into a plausible-but-wrong queue; the id is the
     * one that decides whether there is anything here at all.
     *
     * The date and size read back as null when their keys are absent, which is what a
     * bookmark written by an older build looks like. Sizes and timestamps are never
     * negative, so [NONE] is as free a sentinel here as it is for the ids.
     */
    var resumePoint: ResumePoint?
        get() {
            val itemId = prefs.getLong(KEY_RESUME_ITEM, NONE)
            if (itemId == NONE) return null
            val bucketId = prefs.getLong(KEY_RESUME_BUCKET, NONE)
            return ResumePoint(
                itemId = itemId,
                bucketId = bucketId.takeIf { it != NONE },
                sort = prefs.getString(KEY_RESUME_SORT, null) ?: DEFAULT_SORT,
                shuffle = prefs.getBoolean(KEY_RESUME_SHUFFLE, false),
                seed = prefs.getLong(KEY_RESUME_SEED, 0L),
                itemDateSec = prefs.getLong(KEY_RESUME_DATE, NONE).takeIf { it != NONE },
                itemSizeBytes = prefs.getLong(KEY_RESUME_SIZE, NONE).takeIf { it != NONE },
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                putLong(KEY_RESUME_ITEM, NONE)
            } else {
                putLong(KEY_RESUME_ITEM, value.itemId)
                putLong(KEY_RESUME_BUCKET, value.bucketId ?: NONE)
                putString(KEY_RESUME_SORT, value.sort)
                putBoolean(KEY_RESUME_SHUFFLE, value.shuffle)
                putLong(KEY_RESUME_SEED, value.seed)
                putLong(KEY_RESUME_DATE, value.itemDateSec ?: NONE)
                putLong(KEY_RESUME_SIZE, value.itemSizeBytes ?: NONE)
            }
        }

    private companion object {
        const val FILE = "swipey.home"
        const val KEY_ALBUMS_AS_GRID = "albums_as_grid"
        const val KEY_RESUME_ITEM = "resume_item"
        const val KEY_RESUME_BUCKET = "resume_bucket"
        const val KEY_RESUME_SORT = "resume_sort"
        const val KEY_RESUME_SHUFFLE = "resume_shuffle"
        const val KEY_RESUME_SEED = "resume_seed"
        const val KEY_RESUME_DATE = "resume_date"
        const val KEY_RESUME_SIZE = "resume_size"

        /** MediaStore ids are positive and bucket ids are non-negative, so -1 is free. */
        const val NONE = -1L
        const val DEFAULT_SORT = "NEWEST"
    }
}
