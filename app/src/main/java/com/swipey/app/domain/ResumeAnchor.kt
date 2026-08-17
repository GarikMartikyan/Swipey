package com.swipey.app.domain

/**
 * The place a photograph held in a queue, kept so that a bookmark can outlive it.
 *
 * An id alone cannot survive a deletion: once the row is gone there is nothing left to
 * compare against, and "the card after that one" has no answer. These three fields are
 * exactly what every queue order reads — the date for [SortMode.NEWEST] and
 * [SortMode.OLDEST], the size for [SortMode.LARGEST] and [SortMode.SMALLEST], the id to
 * break a tie — so a photograph's position stays computable after the photograph itself has
 * been trashed.
 */
data class QueuePlace(val id: Long, val dateAddedSec: Long, val sizeBytes: Long)

/**
 * A bookmark resolved against the library as it stands.
 *
 * @property item the photograph to show for it. The bookmarked one while it is still there;
 *   otherwise its nearest surviving neighbour — the card that followed it if there is one,
 *   and the card before it if there is not.
 * @property after the surviving card to deal *after*, or null to deal from the top. Null is
 *   the honest answer when nothing in the queue precedes the bookmark any more: the position
 *   it named is the front of the queue.
 */
data class ResumeAnchor(val item: MediaItem, val after: MediaItem?)

/**
 * Where a resume bookmark points, now that the library has moved underneath it.
 *
 * ### Why this exists
 * The bookmark names the last photograph the user *decided on*, and the likeliest way to
 * end a session is to mark that photograph and commit it — so the single most common state
 * for a bookmark to be found in is: naming something that no longer exists. Resolving it by
 * exact id alone meant the Recent tile went dark precisely after the action it is there to
 * follow, and the tile is not the whole of it: the deck's `startAfter` fails on a missing id
 * too, and silently deals from the top of the queue instead of from where the user stopped.
 *
 * ### What it answers with
 * The card that *followed* the missing one, in the order that queue was dealt in — the
 * position the user would have carried on from. That neighbour is almost always alive:
 * everything ahead of the bookmark is undecided, and only decided photographs get marked and
 * committed. When there is nothing ahead — the bookmark was on the last card — it answers
 * with the nearest survivor *behind* it instead, which is the end of a queue the user has
 * finished, and reads as such.
 *
 * Nearest survivor rather than a remembered neighbour id, because a commit takes a whole run
 * of photographs at once and any single neighbour is as likely to have gone with it.
 *
 * [order] is the queue's own order and not the gallery's: the card after a photograph in a
 * largest-first pass is a different one than in a newest-first pass, and offering the wrong
 * one would resume somewhere the user has never been. A shuffled queue has no order that can
 * outlive a deletion — the same seed over a library one picture shorter is a different
 * permutation altogether — so callers resuming a shuffle pass a plain order here and get
 * "a picture from near where you stopped", which is the most that was ever true of it.
 *
 * One pass, no sort: this runs on every visit to Home, over a gallery that can run to five
 * figures.
 */
fun List<MediaItem>.resumeAnchor(place: QueuePlace, order: SortMode): ResumeAnchor? {
    firstOrNull { it.id == place.id }?.let { return ResumeAnchor(item = it, after = it) }

    val queue = order.queueOrder()
    // The missing photograph, carrying only what an order reads. Comparing against a
    // stand-in rather than against unpacked fields is what keeps this and [sortedFor]
    // from being able to disagree about which way round a queue runs.
    val ghost = place.standIn()
    var next: MediaItem? = null
    var previous: MediaItem? = null
    for (item in this) {
        if (queue.compare(item, ghost) > 0) {
            if (next == null || queue.compare(item, next) < 0) next = item
        } else {
            if (previous == null || queue.compare(item, previous) > 0) previous = item
        }
    }
    val item = next ?: previous ?: return null
    return ResumeAnchor(item = item, after = previous)
}

/**
 * The order [sortedFor] deals in, as a comparator, with the id as a tiebreak.
 *
 * The tiebreak is this function's one departure from `sortedFor`, whose sort is stable and
 * so leaves same-second items in whatever order MediaStore returned them. It is needed
 * here and not there: a total order is the only kind that can place a photograph which is
 * no longer in the list, and `DATE_ADDED` has one-second resolution, so a burst of shots
 * routinely lands several rows on the same value. The id is monotonic, so this agrees with
 * the stable sort in the ordinary case and is merely deterministic in the tied one.
 */
private fun SortMode.queueOrder(): Comparator<MediaItem> = when (this) {
    SortMode.NEWEST -> compareByDescending<MediaItem> { it.dateAddedSec }.thenByDescending { it.id }
    SortMode.OLDEST -> compareBy<MediaItem> { it.dateAddedSec }.thenBy { it.id }
    SortMode.LARGEST -> compareByDescending<MediaItem> { it.sizeBytes }.thenByDescending { it.id }
    SortMode.SMALLEST -> compareBy<MediaItem> { it.sizeBytes }.thenBy { it.id }
}

/** The missing photograph as something a comparator can take. Never shown, never returned. */
private fun QueuePlace.standIn() = MediaItem(
    id = id,
    isVideo = false,
    sizeBytes = sizeBytes,
    dateAddedSec = dateAddedSec,
    durationMs = null,
    bucketId = 0L,
    bucketName = "",
    displayName = "",
)
