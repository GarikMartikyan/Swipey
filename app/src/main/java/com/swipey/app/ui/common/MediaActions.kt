package com.swipey.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem

/**
 * The two ways a photograph leaves Swipey without being decided on.
 *
 * ### Neither of these is a decision
 * Everything else the deck can do to a photograph is a judgement that gets recorded. These do
 * not touch the session, the marks or the database: they hand a `content://` URI to another
 * app and step out of the way. A shared photograph is still undecided when the user comes
 * back, and still on the same card.
 *
 * ### Why the URI needs no FileProvider
 * Swipey never copies a file and never sees a path — a `MediaStore` id is the whole of what it
 * holds. `content://media/external/images/media/1234` is already a URI every app on the phone
 * can resolve, so the grant is a read flag on an intent rather than an exported provider of
 * our own. That is also why sharing works without the app having a single byte of storage
 * permission beyond the read it already asked for.
 *
 * ### Failure is a no-op, not a crash
 * A phone with nothing registered for `ACTION_VIEW` on an image is unusual and entirely
 * possible — a stripped ROM, a work profile with the gallery blocked. `startActivity` throws
 * [ActivityNotFoundException] there, and from a Compose click handler that reaches the
 * Recomposer and takes the Activity down. Both calls below swallow it and report false, so
 * the caller can say something instead of dying.
 */
class MediaActions internal constructor(private val context: Context) {

    /**
     * Opens one item in whatever app the user views photographs in.
     *
     * A chooser is deliberately *not* forced. "View in gallery" means the gallery — the app
     * they have already told the system they want for this — and putting a chooser in front
     * of that would ask a question the button has already answered.
     */
    fun viewInGallery(item: MediaItem): Boolean = launch(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri(), item.mimeType())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )

    /** Shares one item through the system sheet. */
    fun share(item: MediaItem): Boolean = launch(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType()
                putExtra(Intent.EXTRA_STREAM, item.uri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )

    /**
     * Shares several items at once.
     *
     * `ACTION_SEND_MULTIPLE` with a wildcard type when the selection mixes photos and videos:
     * a receiver told it is getting images and handed an mp4 is entitled to refuse the lot,
     * and a batch that silently dropped the videos would be worse than asking a broader app.
     * A single-item selection still goes through [share], because `SEND_MULTIPLE` with one
     * URI is refused outright by a surprising number of receivers.
     */
    fun share(items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        if (items.size == 1) return share(items.first())
        val uris = ArrayList<Uri>(items.map { it.uri() })
        val type = when {
            items.all { !it.isVideo } -> "image/*"
            items.all { it.isVideo } -> "video/*"
            else -> "*/*"
        }
        return launch(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    this.type = type
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                null,
            ),
        )
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // A permission revoked between the query that produced this item and the tap.
        false
    }

    private fun MediaItem.uri(): Uri = contentUriFor(id, isVideo)

    /**
     * Broad on purpose. The exact subtype is in MediaStore, but Swipey does not read it, and a
     * wrong guess — image/jpeg for a HEIC — is worse than a general one: receivers filter on
     * the type they are given, and the wildcard form matches every app that handles pictures.
     */
    private fun MediaItem.mimeType(): String = if (isVideo) "video/*" else "image/*"
}

@Composable
fun rememberMediaActions(): MediaActions {
    val context = LocalContext.current
    return remember(context) { MediaActions(context) }
}
