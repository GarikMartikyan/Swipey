package com.swipey.app.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.swipey.app.domain.MediaItem

/**
 * Never MediaStore.Files — the Files collection has no whole-collection access
 * shortcut and would apply ownership filtering. See spec §5.2.
 */
fun collectionUriFor(isVideo: Boolean): Uri =
    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

fun MediaItem.contentUri(): Uri = ContentUris.withAppendedId(collectionUriFor(isVideo), id)

fun contentUriFor(id: Long, isVideo: Boolean): Uri =
    ContentUris.withAppendedId(collectionUriFor(isVideo), id)
