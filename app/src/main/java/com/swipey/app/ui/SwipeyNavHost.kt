package com.swipey.app.ui

object Routes {
    const val HOME = "home"
    const val SORT = "sort"
    const val DECK = "deck?bucketId={bucketId}&sort={sort}&shuffle={shuffle}&seed={seed}&after={after}"
    const val REVIEW = "review"
    const val BIN = "bin"

    /**
     * @param seed the shuffle's ordering. It is a **route argument** rather than something
     *   the Deck route makes up on arrival, because Home now shows the user a thumbnail of
     *   the card a shuffle will open on — and it can only know which card that is by
     *   resolving the shuffle itself. The seed it resolved has to be the seed the deck
     *   deals with or the thumbnail is a lie; carrying it here is what makes the two agree.
     *   Defaulted so the non-shuffle call sites, for which it is inert, don't have to
     *   invent one.
     */
    /**
     * @param after opens on the card *following* this item instead of at the top of the
     *   queue — Home's Recent tile, carrying the user back to where they stopped. It is a
     *   position within a freshly dealt queue, not a session being restored: the other
     *   arguments still say which queue, and they have to match the one the item was decided
     *   in or the position means nothing. See `HomePreferences.ResumePoint`.
     *
     * There used to be a `resume` argument here as well, which entered the deck without
     * rebuilding its session at all. Its one caller was the return from a committed trash,
     * and a commit now ends the trip and goes to Home instead — so every entry into the deck
     * deals a fresh pass, and this argument only chooses where in it to start.
     */
    fun deck(
        bucketId: Long? = null,
        sort: String = "NEWEST",
        shuffle: Boolean = false,
        seed: Long = System.currentTimeMillis(),
        after: Long? = null,
    ) = "deck?bucketId=${bucketId ?: -1L}&sort=$sort&shuffle=$shuffle&seed=$seed&after=${after ?: -1L}"
}
