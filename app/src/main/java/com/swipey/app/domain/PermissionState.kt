package com.swipey.app.domain

/**
 * Spec §6. PARTIAL blocks the app: without full access the Bin cannot enumerate
 * trashed items, so a deletion could not be shown to be recoverable.
 */
enum class MediaAccess { FULL, PARTIAL, DENIED }

fun resolveMediaAccess(
    imagesGranted: Boolean,
    videoGranted: Boolean,
    userSelectedGranted: Boolean,
): MediaAccess = when {
    imagesGranted && videoGranted -> MediaAccess.FULL
    userSelectedGranted -> MediaAccess.PARTIAL
    else -> MediaAccess.DENIED
}
