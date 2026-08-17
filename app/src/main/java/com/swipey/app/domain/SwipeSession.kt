package com.swipey.app.domain

enum class Decision { KEEP, MARK }

data class UndoResult(val item: MediaItem, val previousDecision: Decision)

/**
 * The swipe state machine. Pure Kotlin — no android.* imports (spec §10).
 *
 * `marked` is a LinkedHashMap so marked() preserves the order the user swiped in,
 * which is the order the Review grid shows.
 */
class SwipeSession(private val queue: List<MediaItem>) {

    private val marked = LinkedHashMap<Long, MediaItem>()
    private val history = ArrayDeque<Pair<MediaItem, Decision>>()

    var position: Int = 0
        private set

    val total: Int get() = queue.size
    val current: MediaItem? get() = queue.getOrNull(position)
    val isExhausted: Boolean get() = position >= queue.size
    val markedCount: Int get() = marked.size
    val markedBytes: Long get() = marked.values.sumOf { it.sizeBytes }

    /**
     * The item [offset] places ahead of [current], or null if the queue ends first.
     *
     * The deck draws `peek(1)` underneath the card being swiped, so the next photograph is
     * already on screen when the current one leaves rather than appearing into bare canvas
     * afterwards. Bounds-safe at both ends, so a caller near the end of the queue does not
     * have to check first.
     */
    fun peek(offset: Int): MediaItem? = queue.getOrNull(position + offset)

    fun swipeLeft(): MediaItem? = advance(Decision.MARK)

    fun swipeRight(): MediaItem? = advance(Decision.KEEP)

    private fun advance(decision: Decision): MediaItem? {
        val item = current ?: return null
        if (decision == Decision.MARK) marked[item.id] = item
        history.addLast(item to decision)
        position++
        return item
    }

    fun undo(): UndoResult? {
        val (item, decision) = history.removeLastOrNull() ?: return null
        marked.remove(item.id)
        position--
        return UndoResult(item, decision)
    }

    fun unmark(id: Long) {
        marked.remove(id)
    }

    fun marked(): List<MediaItem> = marked.values.toList()
}
