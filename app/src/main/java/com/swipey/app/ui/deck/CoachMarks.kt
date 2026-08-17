package com.swipey.app.ui.deck

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.common.Copy
import com.swipey.app.domain.BinSide
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.settings.currentBinSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PrefsFile = "swipey_ui"
private const val KeyCoachMarksSeen = "deck_coach_marks_seen"

/**
 * Whether the first-run hint over the deck has been shown yet.
 *
 * A single boolean in [SharedPreferences] — not a Room row. It describes this
 * installation's interface, has nothing to do with the user's media, and a schema
 * migration to carry one flag would be a migration to maintain forever.
 *
 * Obtain one with [rememberDeckCoachMarks].
 */
@Stable
class DeckCoachMarks internal constructor() {

    private var prefs: SharedPreferences? = null

    /**
     * Starts *false*, and is only ever raised once the stored flag has actually been
     * read. A first-run hint that flashes up for one frame on every launch and then
     * disappears would be worse than never showing it at all.
     */
    var visible by mutableStateOf(false)
        private set

    /** Assume seen until the store says otherwise; see [visible]. */
    private var seen = true

    internal fun onLoaded(store: SharedPreferences, alreadySeen: Boolean) {
        prefs = store
        seen = alreadySeen
        visible = !alreadySeen
    }

    /**
     * Take the hint away, for good. Called by the hint's own button and by the user's
     * first recorded decision — someone who has just swiped a card has been taught what
     * the hint teaches.
     *
     * Writes through `apply()`, which persists off the main thread and is flushed before
     * the process can exit, so the hint cannot come back after a kill.
     */
    fun dismiss() {
        visible = false
        if (!seen) {
            seen = true
            prefs?.edit()?.putBoolean(KeyCoachMarksSeen, true)?.apply()
        }
    }
}

/**
 * Reads the stored flag off the main thread and returns the state it drives.
 *
 * The first `getSharedPreferences` call touches the disk; on the deck, which opens
 * straight onto a decoded photograph, that read has no business competing with the first
 * frame.
 */
@Composable
fun rememberDeckCoachMarks(): DeckCoachMarks {
    val context = LocalContext.current
    val state = remember { DeckCoachMarks() }
    LaunchedEffect(Unit) {
        val (store, seen) = withContext(Dispatchers.IO) {
            val store = context.applicationContext.getSharedPreferences(PrefsFile, Context.MODE_PRIVATE)
            store to store.getBoolean(KeyCoachMarksSeen, false)
        }
        state.onLoaded(store, seen)
    }
    return state
}

/**
 * The first-run hint, over the very first card.
 *
 * ### It does not take the gesture away
 * Nothing in here handles a pointer except the "Got it" button. The panel has a
 * background and a border, neither of which makes a node a touch target, so a swipe
 * that starts on top of the hint reaches the card underneath exactly as it would
 * anywhere else — which matters, because the hint's entire content is an instruction to
 * swipe.
 */
@Composable
fun DeckCoachMarkOverlay(modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val colors = SwipeyTheme.colors
    val shape = RoundedCornerShape(SwipeyRadius.card)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = SwipeySpacing.xl)
                .widthIn(max = 420.dp)
                .clip(shape)
                // Very slightly translucent: the photograph the hint is describing stays
                // faintly visible behind it, which is the whole point of the hint.
                .background(colors.surface.copy(alpha = 0.94f))
                .border(SwipeySize.hairline, colors.hairline, shape)
                // Vertical only, so a horizontal swipe — the gesture this panel exists to
                // teach — still reaches the card underneath. Present so the hint can't
                // clip its own button off the bottom of a small screen at a large font
                // scale.
                .verticalScroll(rememberScrollState())
                .padding(SwipeySpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwipeyText(
                Copy.COACH_TITLE,
                style = SwipeyTheme.typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwipeySpacing.lg))

            // Left first and right second whichever way the deck is set, because the
            // chevrons point where the words say and a panel that read right-then-left
            // would fight itself. What changes is which outcome each line carries.
            val binOnLeft = currentBinSide == BinSide.LEFT
            Hint(
                text = if (binOnLeft) Copy.coachBin(true) else Copy.coachKeep(false),
                leading = {
                    SwipeyIcon(
                        SwipeyIcons.ChevronLeft,
                        contentDescription = null,
                        tint = if (binOnLeft) colors.bin else colors.keep,
                        size = 18.dp,
                    )
                },
            )
            Spacer(Modifier.height(SwipeySpacing.sm))
            Hint(
                text = if (binOnLeft) Copy.coachKeep(true) else Copy.coachBin(false),
                trailing = {
                    SwipeyIcon(
                        SwipeyIcons.ChevronRight,
                        contentDescription = null,
                        tint = if (binOnLeft) colors.keep else colors.bin,
                        size = 18.dp,
                    )
                },
            )

            Spacer(Modifier.height(SwipeySpacing.lg))
            SwipeyText(
                Copy.COACH_REASSURE,
                style = SwipeyTheme.typography.label,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwipeySpacing.lg))
            SwipeyButton(
                text = Copy.COACH_DISMISS,
                onClick = onDismiss,
                variant = SwipeyButtonVariant.Ghost,
            )
        }
    }
}

/** One direction, with the chevron on the side it points. */
@Composable
private fun Hint(
    text: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        SwipeyText(
            text,
            style = SwipeyTheme.typography.body,
            color = SwipeyTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        trailing?.invoke()
    }
}
