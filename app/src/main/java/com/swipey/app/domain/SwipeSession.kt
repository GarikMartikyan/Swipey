package com.swipey.app.domain

enum class Decision { KEEP, MARK }

data class UndoResult(val item: MediaItem, val previousDecision: Decision)

/**
 * A photograph the deck has already passed, and what is currently true of it.
 *
 * [kept] rather than a [Decision] because it is not a record of the swipe — it is the
 * present state, which the grid can change after the fact. See [SwipeSession.decided].
 *
 * **Null means passed without being decided**, which only became reachable when the grid
 * gained the ability to start the deck from any card: everything jumped over is behind the
 * position with no decision attached to it. Before that, behind the position and decided
 * were the same thing, and this was a plain `Boolean`. The filmstrip draws nothing for a
 * null — an item nobody has judged must not read as kept.
 */
data class DecidedItem(val item: MediaItem, val kept: Boolean?)

/**
 * The swipe state machine. Pure Kotlin — no android.* imports (spec §10).
 *
 * `marked` is a LinkedHashMap so marked() preserves the order the user swiped in,
 * which is the order the Review grid shows.
 */
class SwipeSession(queue: List<MediaItem>) {

    /**
     * The queue is a `var` for exactly one reason: [drop]. A session used to be fixed for
     * its whole life, and it still is from the deck's point of view — nothing here adds,
     * reorders or re-serves anything. Items only ever leave, and only because they have
     * left the library underneath the session.
     */
    private var queue: List<MediaItem> = queue

    private val marked = LinkedHashMap<Long, MediaItem>()

    /**
     * The items the user has explicitly kept.
     *
     * The counterpart to [marked], and new alongside [jumpTo]. Keep used to need no record
     * at all — it was the absence of a mark, and since the deck could only move forward one
     * card at a time, "behind the position and not marked" meant "kept". Jumping breaks that
     * equivalence: everything leapt over is also behind the position and also not marked,
     * and calling it kept would put a tick on a photograph nobody has looked at.
     *
     * So keep is now a record like marking is, and the two sets together answer the only
     * question the strip and the grid ask: has this been decided, and which way.
     */
    private val kept = LinkedHashSet<Long>()

    private val history = ArrayDeque<Swiped>()

    /**
     * One decision, and **where the deck was standing when it was made**.
     *
     * The position is what [undo] restores. It used to simply decrement, which was the same
     * thing back when the only way to reach a card was to swipe the one before it. After a
     * [jumpTo] it is not: undoing then would step back one card from wherever the jump
     * landed, unmarking a photograph the user decided on somewhere else entirely. Recording
     * the position makes undo mean what it says — go back to the last decision I made.
     */
    private data class Swiped(val item: MediaItem, val decision: Decision, val at: Int)

    /**
     * Built on first use and thrown away by [drop]. [setMarked] takes an id rather than an
     * item — the grid hands back what it was given — and a linear scan per tap over a queue
     * that can run to five figures is a cost nobody needs to pay twice.
     */
    private var byIdCache: Map<Long, MediaItem>? = null
    private val byId: Map<Long, MediaItem>
        get() = byIdCache ?: queue.associateBy { it.id }.also { byIdCache = it }

    var position: Int = 0
        private set

    val total: Int get() = queue.size
    val current: MediaItem? get() = queue.getOrNull(position)
    val isExhausted: Boolean get() = position >= queue.size
    val markedCount: Int get() = marked.size
    val markedBytes: Long get() = marked.values.sumOf { it.sizeBytes }

    /** The whole queue, in the order the deck will serve it. What the grid lists. */
    val items: List<MediaItem> get() = queue

    /**
     * Which items are marked, as a snapshot.
     *
     * A copy rather than [marked]'s own key set: the grid holds this in Compose state, and
     * a live view of a mutable map is a value that changes without anything having said so.
     */
    val markedIds: Set<Long> get() = marked.keys.toSet()

    /**
     * Which items have been explicitly kept, as a snapshot. A copy, for the same reason
     * [markedIds] is one.
     *
     * The filmstrip's other half needs this: a card *ahead* of the deck can already carry a
     * decision — marked from the grid, or kept and then jumped back past — and the strip has
     * to say so rather than showing it as untouched.
     */
    val keptIds: Set<Long> get() = kept.toSet()

    /**
     * The item [offset] places ahead of [current], or null if the queue ends first.
     *
     * The deck draws `peek(1)` underneath the card being swiped, so the next photograph is
     * already on screen when the current one leaves rather than appearing into bare canvas
     * afterwards. Bounds-safe at both ends, so a caller near the end of the queue does not
     * have to check first.
     */
    fun peek(offset: Int): MediaItem? = queue.getOrNull(position + offset)

    /**
     * The item this session holds under [id], or null if it never held one.
     *
     * Backed by [byId], so callers that only have an id — the grid hands back what it was
     * given, and so does the card's commit animation — can reach the whole photograph
     * without a scan. The bookmark needs it: a resume point records where a photograph sat
     * in the queue's order, not only which one it was.
     */
    fun itemFor(id: Long): MediaItem? = byId[id]

    /**
     * The last [count] photographs the deck has passed, oldest first.
     *
     * The filmstrip's left-hand side. Oldest first because the strip lays the session out
     * in its own order and runs it through the centre, so the item nearest the current card
     * has to be the last one here.
     *
     * ### Read from the marks, not from the history
     * [history] holds what each swipe *was*, and this deliberately ignores it. A mark can be
     * edited from the grid after the deck has passed the photograph, so the swipe and the
     * present state can disagree — and it is the present state that the grid shows, that the
     * counter counts, and that Review will act on. Deriving [DecidedItem.kept] from the mark
     * set means the strip cannot say "kept" about something on its way to the bin.
     *
     * Bounded rather than whole: the strip draws a handful either side of centre, and a
     * session can run to five figures.
     */
    fun decided(count: Int): List<DecidedItem> {
        if (count <= 0) return emptyList()
        return (maxOf(0, position - count) until position).map { index ->
            val item = queue[index]
            DecidedItem(item, kept = decisionFor(item.id)?.let { it == Decision.KEEP })
        }
    }

    /**
     * What is currently true of one item, wherever it sits relative to the deck.
     *
     * Null is a real answer and the common one: most of a session has not been judged. The
     * strip and the grid both read this, which is what stops "behind the deck" and "decided"
     * from being confused with each other now that [jumpTo] can separate them.
     */
    fun decisionFor(id: Long): Decision? = when {
        marked.containsKey(id) -> Decision.MARK
        kept.contains(id) -> Decision.KEEP
        else -> null
    }

    fun swipeLeft(): MediaItem? = advance(Decision.MARK)

    fun swipeRight(): MediaItem? = advance(Decision.KEEP)

    private fun advance(decision: Decision): MediaItem? {
        val item = current ?: return null
        // Keep clears a mark rather than ignoring it. Marks can now be made ahead of the
        // deck, from the grid, so by the time a card is reached it may already be marked —
        // and a right-swipe that left it marked would record the opposite of the decision
        // the user just made. Before the grid existed this branch was unreachable, which is
        // why it was not here.
        if (decision == Decision.MARK) {
            marked[item.id] = item
            kept.remove(item.id)
        } else {
            marked.remove(item.id)
            kept.add(item.id)
        }
        history.addLast(Swiped(item, decision, position))
        position++
        return item
    }

    /**
     * Undoes the last decision and puts the deck back where that decision was made.
     *
     * Both halves matter: the mark (or the keep) is lifted, and the position returns to the
     * card it was about. See [Swiped] for why that is recorded rather than counted.
     */
    fun undo(): UndoResult? {
        val last = history.removeLastOrNull() ?: return null
        marked.remove(last.item.id)
        kept.remove(last.item.id)
        position = last.at.coerceIn(0, queue.size)
        return UndoResult(last.item, last.decision)
    }

    /**
     * Deals from [id] instead of from wherever the deck had got to.
     *
     * The grid's other tap: everything in it is a card the deck will serve, and tapping one
     * says "that one, now". Nothing is decided by jumping — not the card jumped to, and not
     * the ones jumped over, which stay in the queue with no decision attached to them and
     * are simply passed by for this session.
     *
     * Returns false for an id the session does not hold, on the same reasoning as
     * [setMarked]: the grid can only offer what the queue gave it, so an unknown id means
     * the session was rebuilt underneath the caller and the tap describes a photograph this
     * session has never had.
     */
    fun jumpTo(id: Long): Boolean {
        val index = indexOf(id)
        if (index < 0) return false
        position = index
        return true
    }

    /**
     * Deals from the card *after* [id] — how a session picks up where the last one stopped.
     *
     * Home remembers the last photograph the user decided on and offers to carry on from it;
     * carrying on means the next one, not the one they have already judged. Landing exactly
     * at the end is a real outcome and left alone: an album finished last time reopens
     * exhausted, which is the truth about it.
     */
    fun startAfter(id: Long): Boolean {
        val index = indexOf(id)
        if (index < 0) return false
        position = (index + 1).coerceAtMost(queue.size)
        return true
    }

    /**
     * A linear scan, and deliberately: this runs once per tap or once per session, against
     * [byId]'s cost of building a map of the whole queue for a single lookup.
     */
    private fun indexOf(id: Long): Int = queue.indexOfFirst { it.id == id }

    fun unmark(id: Long) {
        marked.remove(id)
    }

    /**
     * Marks or unmarks any item in the queue, without moving the deck.
     *
     * The grid's edit. Deliberately position-blind: marking something twenty cards ahead
     * must not advance past the cards between, and unmarking something already passed must
     * not rewind onto it. Unknown ids are ignored rather than throwing — the grid can only
     * offer what the queue gave it, so an unknown id means the session was rebuilt beneath
     * the caller, and the right answer to a decision about a photograph that is no longer
     * in this session is to drop it.
     *
     * There is no third state. Marked or not is the whole vocabulary, exactly as it is in
     * the deck, where keep is the absence of a mark rather than a record of its own.
     */
    fun setMarked(id: Long, marked: Boolean) {
        if (marked) {
            val item = byId[id] ?: return
            this.marked[id] = item
            kept.remove(id)
        } else {
            this.marked.remove(id)
            // Position-blind about *movement*, but not about meaning. Unticking a card the
            // deck has already passed is a decision — "no, keep this one" — and the strip
            // should show it as kept. Unticking one still ahead is not: it takes the item
            // back to undecided, which is what it was before the tick.
            if (queue.indexOfFirst { it.id == id }.let { it in 0 until position }) kept.add(id)
        }
    }

    fun marked(): List<MediaItem> = marked.values.toList()

    /**
     * Takes [ids] out of the session entirely — queue, marks and history.
     *
     * What this is for: the user has committed, MediaStore has trashed those photographs,
     * and the deck is about to be resumed. They are no longer in the library, so a session
     * that still listed them would be describing a gallery that no longer exists — the grid
     * would offer them, the filmstrip would try to draw thumbnails MediaStore will not
     * serve, and every one of them would read as *kept*, since a mark is the only thing
     * distinguishing the two and their marks are exactly what was just acted on.
     *
     * ### Position moves with the queue, not against it
     * The card on screen must still be the card on screen afterwards. Removing an item
     * behind the current position shortens the queue in front of it, so the position has to
     * come back by however many were removed *before* it; removing one ahead changes
     * nothing. Without that correction a commit made mid-session would silently skip as
     * many cards as it trashed.
     *
     * History is filtered rather than cleared, so undo still works for anything decided
     * before the commit — but it can no longer step back onto a photograph that is now in
     * the trash, which would be an undo the app cannot honour.
     */
    fun drop(ids: Set<Long>) {
        if (ids.isEmpty()) return
        // How many of the dropped items sit before each index of the queue as it stands.
        // One prefix count answers it for the position *and* for every recorded position in
        // the history, which are all indices into the queue that is about to change shape.
        val droppedBefore = IntArray(queue.size + 1)
        for (index in queue.indices) {
            droppedBefore[index + 1] = droppedBefore[index] + if (queue[index].id in ids) 1 else 0
        }
        val removedBefore = droppedBefore[minOf(position, queue.size)]

        queue = queue.filterNot { it.id in ids }
        ids.forEach { marked.remove(it) }
        kept.removeAll(ids)

        val survivors = history.filterNot { it.item.id in ids }
            .map { it.copy(at = (it.at - droppedBefore[it.at]).coerceIn(0, queue.size)) }
        history.clear()
        history.addAll(survivors)

        position = (position - removedBefore).coerceIn(0, queue.size)
        byIdCache = null
    }
}
