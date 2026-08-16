package com.swipey.app.domain

/**
 * targetSdk 36 enables LIMIT_CREATE_REQUEST_URIS, which throws above 2000 URIs
 * per createTrashRequest. 500 keeps a comfortable margin. See spec §8.
 */
const val MAX_URIS_PER_REQUEST = 500

fun <T> List<T>.chunkedForRequest(size: Int = MAX_URIS_PER_REQUEST): List<List<T>> =
    if (isEmpty()) emptyList() else chunked(size)
