package com.swipey.app.ui

object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val SORT = "sort"
    const val ALBUMS = "albums"
    const val DECK = "deck?bucketId={bucketId}&sort={sort}&shuffle={shuffle}"
    const val REVIEW = "review"
    const val RESULT = "result"
    const val BIN = "bin"

    fun deck(bucketId: Long? = null, sort: String = "NEWEST", shuffle: Boolean = false) =
        "deck?bucketId=${bucketId ?: -1L}&sort=$sort&shuffle=$shuffle"
}
