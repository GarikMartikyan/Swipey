package com.swipey.app.ui

object Routes {
    const val HOME = "home"
    const val SORT = "sort"
    const val DECK = "deck?bucketId={bucketId}&sort={sort}&shuffle={shuffle}&seed={seed}"
    const val REVIEW = "review"
    const val RESULT = "result"
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
    fun deck(
        bucketId: Long? = null,
        sort: String = "NEWEST",
        shuffle: Boolean = false,
        seed: Long = System.currentTimeMillis(),
    ) = "deck?bucketId=${bucketId ?: -1L}&sort=$sort&shuffle=$shuffle&seed=$seed"
}
