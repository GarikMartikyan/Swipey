package com.swipey.app.ui.deck

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.CaptionContrast
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.domain.megapixels
import com.swipey.app.domain.relativeAge
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyCaptionGradient
import com.swipey.app.ui.design.SwipeyCaptionScrim
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyDivider
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyMotion
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySheet
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import java.util.Calendar
import com.swipey.app.ui.design.rememberSwipeyHaptics
import com.swipey.app.ui.design.SwipeyIconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.combinedClickable

/**
 * What this photograph is: the album it came from, and what it costs.
 *
 * ### Why anything at all is on the photograph
 * The deck is built to keep chrome off the picture — the card is inset, the counter sits
 * above it, and every earlier addition was refused on those grounds. This one earns its
 * place because it changes the decision rather than describing it: "is this worth keeping"
 * is not answerable without knowing what it costs, and a 40 MB burst frame and a 300 KB
 * screenshot are the same rectangle until something says otherwise. The date does the other
 * half — it is what tells you whether you are looking at last week or at 2019, which is most
 * of what makes a photograph disposable. The album is the third: "Camera" and "WhatsApp"
 * are two different questions being asked about the same file size.
 *
 * Everything else about the item — what it is called, how many pixels — is interesting
 * rather than decisive, so it goes behind [MediaInfoSheet] instead.
 *
 * ### It is a caption, and it is Home's caption
 * This is not a badge with a shape of its own. It is the same object Home uses to name an
 * album over its cover, moved onto the deck: the name line in `body`, the numbers under it
 * in `labelNumeric` and [SwipeyColors.textSecondary], the block inset by [SwipeySpacing.lg]
 * from the corner, and the control as a 40dp disc in the corner where Home's hero puts its
 * sort button. Two screens that caption a photograph should not caption it two different
 * ways; anything that reads as a *new* treatment here is a bug.
 *
 * ### Top, not bottom — because the bottom belongs to video
 * Home captions along the bottom of a cover and this started there too. It moved up when
 * the video card grew a timeline: a scrubber has to be within reach of a thumb, which puts
 * it along the bottom edge, and two stacked blocks of chrome down there left the picture
 * squeezed between them. Splitting them puts what the card *is* at the top and what you can
 * *do* with it at the bottom, and it means a photograph and a video now carry their chrome
 * in the same places rather than one shuffling the other's out of the way.
 *
 * ### The ground is computed, not chosen
 * It is still [SwipeyCaptionGradient], but nobody picks how dark it is any more. The card
 * measures the top of its own photograph and [CaptionContrast] solves for the least scrim
 * that puts the smaller line at WCAG AA over *that* frame — nothing at all on a dark
 * picture, a real one on a snow field. Every fixed value tried before this was wrong twice:
 * too heavy on the photographs that needed nothing, and still too light on the ones that
 * did.
 *
 * Two decisions make that solvable rather than a grind. The second line is [VibrancyInk] —
 * a translucency of the primary ink rather than a fixed grey, so it moves with its own
 * background instead of drifting into it. And the album line is set at 19sp bold, which
 * puts it over the standard's large-text threshold and drops its own requirement from 4.5:1
 * to 3:1. Both of them buy the same thing: less darkness on the photograph.
 *
 * The one thing that is not Home's is the control's ground. The disc is frosted rather than
 * flat: the picture blurred behind it instead of covered over, which keeps a round object
 * from reading as a hole punched in the photograph the way a solid black disc does at this
 *
 * It is drawn *inside* the card rather than over the stage, which means it flies off with the
 * photograph it describes; a caption that stayed behind while its picture left would, for the
 * length of the animation, be describing the wrong thing.
 *
 * The caption states, the disc acts. Keeping them separate is what lets the caption stay out
 * of the semantics tree as plain text while the button carries a name a screen reader finds.
 */
@Composable
fun MediaBadge(item: MediaItem, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // What the photograph is doing where the words go, and therefore how much ground
        // they need. Null until the thumbnail has been read, which is a frame or two —
        // [FallbackLuma] covers that window with the value a mid photograph would produce.
        val measured = rememberTopStripLuma(item)
        val target = CaptionContrast.scrimAlphaFor(measured ?: FallbackLuma, VibrancyInk)
        // Animated, so the ground settles into place rather than stepping when the
        // measurement lands. Chrome's duration: this is a change *to* the interface, not a
        // change the user asked for, and it should not be an event.
        val scrimAlpha by animateFloatAsState(target, SwipeyMotion.chrome(), label = "captionScrim")

        SwipeyCaptionGradient(
            DeckRampFraction,
            fromTop = true,
            scrim = Color.Black.copy(alpha = scrimAlpha),
        )

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = SwipeySpacing.lg, top = SwipeySpacing.lg)
                // The full width, less the gutter. This used to reserve a touch target's
                // worth of room on the right for the ⓘ; the details sheet is opened by
                // holding the card now, so there is nothing in that corner to dodge and a
                // long album name has the whole line back.
                .padding(end = SwipeySpacing.lg),
        ) {
            SwipeyText(
                item.bucketName,
                style = SwipeyTheme.typography.body.copy(
                    // Large text, in the standard's sense rather than the adjective's: at
                    // 19sp bold this line's bar drops from 4.5:1 to 3:1, which is contrast
                    // the ground no longer has to supply. It also happens to be the right
                    // hierarchy — the album is the thing being named.
                    fontSize = CaptionNameSize,
                    lineHeight = CaptionNameLine,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    shadow = CaptionShadow,
                ),
                color = SwipeyDarkColors.textPrimary,
                maxLines = 1,
            )
            SwipeyText(
                Copy.deckBadge(formatBytes(item.sizeBytes), shortDate(item.dateAddedSec)),
                style = SwipeyTheme.typography.labelNumeric.copy(shadow = CaptionShadow),
                // Not textSecondary. A fixed grey has one luminance whatever is behind it,
                // so on a photograph it is the line that fails first however the ground is
                // tuned — which is exactly what kept happening here. This is the same ink as
                // the line above at a translucency, so it tracks its own background: lighter
                // over a bright frame, and never further from it than it started.
                color = SwipeyDarkColors.textPrimary.copy(alpha = VibrancyInk),
                maxLines = 1,
            )
        }

    }
}

/** The visible disc. Home's `PhotoButtonSize`, because this is Home's button. */
private val BadgeDisc = 40.dp

/** The glyph inside the disc. Leaves 11dp of ground on each side at [BadgeDisc]. */
private val BadgeIconSize = 18.dp

/**
 * How far the visible disc sits from the card's top-right corner: the padding around the
 * touch target, plus the slack between that target and the smaller disc drawn inside it.
 * Derived, so nudging either number keeps the disc's frosted ground aligned.
 */
private val DiscInset = SwipeySpacing.sm + (SwipeySize.touchMin - BadgeDisc) / 2

/**
 * The blur behind the caption and the disc.
 *
 * Enough that no edge in the photograph survives as an edge — which is the whole point,
 * since a legible caption needs its ground to have no detail of its own — and not so much
 * that the block stops taking the picture's colour, which is the only reason to blur rather
 * than to paint.
 */
private val FrostRadius = 24.dp

/**
 * The dark laid over the blur.
 *
 * Blur alone does not guarantee contrast: a blurred white sky is still white. This is what
 * makes the ground legible in the worst case, and at 0.55 it is heavy enough to hold
 * [SwipeyColors.textSecondary] over a bright frame while still letting the picture's hue
 * through. Near-black rather than black, so it sits in the same family as the app's canvas.
 */
private val FrostTint = Color(0xFF0A0B0D).copy(alpha = 0.55f)

/**
 * How much of the card the caption's ramp covers.
 *
 * Home's album tile passes 0.6 for a 1:1 cover, and this started at the same figure. Half a
 * card turned out to be the wrong shape for it: a partly-transparent black laid over the top
 * half of a photograph does not read as a caption ground, it reads as a dirty picture — the
 * eye has the untouched bottom half sitting right next to it for comparison, and calls the
 * difference grime rather than design.
 *
 * At 0.3 the ramp is a band along the top edge instead of a wash over the frame: about 149dp
 * of a 495dp card on a 385dp-wide screen, solid for its first 30% (~45dp) and gone by the
 * time it reaches the picture proper. The caption block stands 56dp including its inset, so
 * its second line sits just past the flat part, at roughly 90% of [DeckRampScrim] — which is
 * why the type carries a shadow rather than trusting the ground alone.
 */
private const val DeckRampFraction = 0.3f

/**
 * The translucency of the caption's second line — Apple's vibrancy, in one number.
 *
 * White at 78% rather than a grey, and the difference is not aesthetic. A grey has a fixed
 * luminance, so as the ground under it changes it drifts toward or away from the ground
 * arbitrarily; over a bright photograph it converges with it and the line disappears. Ink
 * defined as a translucency of the primary tracks whatever is behind it instead, which is
 * what makes a solvable contrast problem out of an unsolvable one.
 *
 * It is also the number [CaptionContrast] solves against, so the two must move together:
 * lightening this ink without re-reading its note will quietly ask the ground for more.
 */
private const val VibrancyInk = 0.78f

/**
 * The brightness assumed for the frame or two before the real measurement lands, and for
 * any photograph whose thumbnail will not load.
 *
 * Deliberately on the bright side of mid. Guessing dark would leave the caption briefly
 * unreadable over a snow field, which is the one failure this whole mechanism exists to
 * prevent; guessing bright costs a slightly heavy ground for a few frames on a dark photo,
 * which nobody will ever notice.
 */
private const val FallbackLuma = 165f

/** The album line: [CaptionContrast]'s large-text threshold is 18.66px, and this clears it. */
private val CaptionNameSize = 19.sp
private val CaptionNameLine = 25.sp

/**
 * The caption's own contrast, carried by the type instead of the ground.
 *
 * Kept even though the ground is now solved for, because the measurement it is solved from
 * is a *mean*: a bright cloud inside an otherwise mid-grey strip moves the average less than
 * it moves the eye. The shadow is what covers that gap, and it is the one legibility aid
 * that costs the picture nothing — at nine pixels of blur it spends its ink inside the
 * letterforms' own gaps, where there was never any photograph to see.
 *
 * The blur is wide relative to the offset, and the offset is barely there: this should read
 * as the letters sitting slightly off the picture, not as a drop shadow.
 */
private val CaptionShadow = Shadow(
    color = Color.Black.copy(alpha = 0.78f),
    offset = Offset(0f, 1f),
    blurRadius = 9f,
)

/** `Modifier.blur` draws nothing at all below API 31, so the ground has to know. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Everything else the app knows about the item.
 *
 * ### The picture identifies itself
 * The sheet opens with the photograph and a three-line table beside it — size, resolution,
 * how long ago — because those three are what the sheet is usually opened to settle, and
 * because a panel of eight equally-weighted rows makes a user read all eight to find one.
 * The thumbnail is there to answer "is this about the card I am looking at", which a filename
 * cannot do at a glance and a picture does instantly.
 *
 * Everything in the table appears again in the rows below, at full precision: the table says
 * "12.2 MP" and the row says "4032 × 3024", the table says "3 days ago" and the row gives the
 * date and the minute. Deliberate duplication — the top is for recognising, the bottom is for
 * checking, and a user doing the second thing should not have to have done the first.
 *
 * Every row below is always present; a missing value shows [Copy.INFO_UNKNOWN] rather than
 * being dropped, so two photographs produce sheets of the same shape and a blank reads as
 * "the phone never recorded this" rather than as a row that failed to render.
 *
 * Drawn in the dark palette in both themes, like every other surface the deck puts over
 * itself: it slides up across a photograph, and a near-white sheet arriving over a lightbox
 * is a flash rather than a panel.
 *
 * It scrolls, because the header plus eight rows does not fit a phone at 200% font scale —
 * and a details sheet is exactly the kind of thing a user turns the text size up for.
 */
@Composable
fun MediaInfoSheet(
    item: MediaItem,
    visible: Boolean,
    onDismiss: () -> Unit,
    /** A plain tap on Share: this one photograph, straight to the system sheet. */
    onShare: () -> Unit = {},
    /**
     * A press and hold on Share.
     *
     * The long press is the only way into the multi-select picker, which makes the hint line
     * at the foot of the sheet load-bearing rather than decorative — an icon cannot say that
     * it has a second gesture, and nothing else here would.
     */
    onShareMany: () -> Unit = {},
    onViewInGallery: () -> Unit = {},
    /**
     * Whether the sheet carries its Share and View-in-gallery glyphs.
     *
     * False inside the share picker, where the user is already choosing what to share: a
     * Share button there would offer a second, smaller way to do the thing the screen exists
     * for, and holding it would offer to open the picker from inside the picker. What is left
     * is the sheet's other half — what this photograph actually is — which is the only reason
     * to open it from a carousel card.
     */
    showActions: Boolean = true,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * SheetHeightFraction

    SwipeyTheme(colors = SwipeyDarkColors) {
        SwipeySheet(
            visible = visible,
            onDismiss = onDismiss,
            title = Copy.INFO_TITLE,
            titleTrailing = if (!showActions) {
                null
            } else {
                {
                    ShareGlyph(onShare = onShare, onShareMany = onShareMany)
                    SwipeyIconButton(
                        icon = SwipeyIcons.Gallery,
                        contentDescription = Copy.VIEW_IN_GALLERY,
                        onClick = onViewInGallery,
                        size = GlyphButton,
                        iconSize = GlyphIcon,
                    )
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                InfoHeader(item)
                InfoRow(Copy.INFO_NAME, item.displayName)
                InfoRow(Copy.INFO_RESOLUTION, resolutionOf(item))
                InfoRow(Copy.INFO_KIND, if (item.isVideo) Copy.INFO_KIND_VIDEO else Copy.INFO_KIND_PHOTO)
                // Only videos have one, and a "Length: —" on every photograph would be a row
                // that exists to say nothing. This is the one omission the sheet allows,
                // because the field does not apply rather than being unrecorded.
                if (item.isVideo) {
                    InfoRow(Copy.INFO_DURATION, item.durationMs?.let(::formatDuration) ?: Copy.INFO_UNKNOWN)
                }
                InfoRow(Copy.INFO_ALBUM, item.bucketName)
                InfoRow(Copy.INFO_ADDED, fullDateTime(item.dateAddedSec))
                InfoRow(Copy.INFO_PATH, item.relativePath ?: Copy.INFO_UNKNOWN, divider = false)
                // The only place the long press is written down. See [onShareMany]. Goes
                // with the glyphs it describes.
                if (showActions) SwipeyText(
                    Copy.SHARE_HINT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SwipeySpacing.md),
                    style = SwipeyTheme.typography.label,
                    color = SwipeyTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(SwipeySpacing.sm))
        }
    }
}

/** How much of the screen the sheet's rows may take before they start scrolling. */
private const val SheetHeightFraction = 0.62f

/**
 * The thumbnail, and the three facts worth reading before any of the rows.
 *
 * The thumbnail is square-cropped rather than shown at the item's own shape. A row of
 * details is a grid of horizontal lines, and a header that changes height with every
 * photograph — tall for a screenshot, short for a panorama — makes the whole sheet jump as
 * the deck advances. The crop costs the edges of a picture that is on screen full-size
 * directly behind this sheet, so nothing is actually hidden.
 *
 * The table's values are right-aligned against the sheet's gutter and set in tabular
 * figures, so the three of them line up as a column rather than reading as three unrelated
 * fragments.
 */
@Composable
private fun InfoHeader(item: MediaItem) {
    val colors = SwipeyTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = SwipeySpacing.xs, bottom = SwipeySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md),
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            // The card behind this sheet is the same photograph, announced there. A screen
            // reader meeting it twice would be told about a picture it has already described.
            contentDescription = null,
            modifier = Modifier
                .size(HeaderThumb)
                .clip(RoundedCornerShape(SwipeyRadius.card))
                .background(colors.surfaceStrong),
            contentScale = ContentScale.Crop,
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            HeaderFact(Copy.INFO_SIZE, formatBytes(item.sizeBytes))
            HeaderFact(Copy.INFO_RESOLUTION, megapixelsOf(item))
            HeaderFact(Copy.INFO_ADDED, Copy.infoAge(relativeAge(daysAgo(item.dateAddedSec))))
        }
    }
}

/** One line of the header's table: a quiet label, and the value pinned to the right. */
@Composable
private fun HeaderFact(label: String, value: String) {
    val colors = SwipeyTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HeaderFactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeyText(
            label,
            modifier = Modifier.weight(1f),
            style = SwipeyTheme.typography.label,
            color = colors.textSecondary,
            maxLines = 1,
        )
        SwipeyText(
            value,
            style = SwipeyTheme.typography.bodyNumeric,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** The header's thumbnail. Large enough to recognise a photograph, not a second card. */
private val HeaderThumb = 108.dp

/** Between the header's three lines. Tight, so the table reads as one block. */
private val HeaderFactGap = 3.dp

/**
 * The header's resolution value: megapixels alone, or the one dimension that is known.
 *
 * The full "4032 × 3024" belongs in the row below. Up here it would be the longest string in
 * a column of three and would break the alignment the table exists to create, for a number
 * nobody reads at this size — "12.2 MP" is the version of that fact a person can hold.
 */
private fun megapixelsOf(item: MediaItem): String {
    val mp = item.megapixels()
    if (mp != null) return Copy.infoMegapixels(mp)
    val width = item.widthPx
    val height = item.heightPx
    return when {
        width != null -> Copy.infoWidthOnly(width)
        height != null -> Copy.infoHeightOnly(height)
        else -> Copy.INFO_UNKNOWN
    }
}

/**
 * Whole days between then and now, counted from midnight to midnight in the device's zone.
 *
 * Midnights rather than elapsed seconds, because "yesterday" is a calendar word: a photograph
 * taken at 11pm and read at 1am is two hours old and was, correctly, taken yesterday.
 * Counting 24-hour blocks would have called it today for another twenty-two hours.
 */
private fun daysAgo(dateAddedSec: Long): Int {
    val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000L }.atMidnight()
    val now = Calendar.getInstance().atMidnight()
    return ((now - then) / 86_400_000L).toInt()
}

private fun Calendar.atMidnight(): Long {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return timeInMillis
}

/** The label column's share of the row. The rest is the value, which wraps into it. */
private const val LabelWeight = 0.38f

@Composable
private fun InfoRow(label: String, value: String, divider: Boolean = true) {
    val colors = SwipeyTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = SwipeySpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            SwipeyText(
                label,
                modifier = Modifier.weight(LabelWeight),
                style = SwipeyTheme.typography.label,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(SwipeySpacing.md))
            SwipeyText(
                value,
                modifier = Modifier.weight(1f - LabelWeight),
                style = SwipeyTheme.typography.body,
                color = colors.textPrimary,
            )
        }
        if (divider) SwipeyDivider()
    }
}

/**
 * The resolution row's value, and the three things it can be.
 *
 * Both dimensions, one of them, or neither. Half-known is worth stating rather than hiding:
 * a width alone still tells the user whether they are looking at a thumbnail or a raw frame,
 * and the megapixel figure is simply left off, since it cannot be computed from one side.
 */
private fun resolutionOf(item: MediaItem): String {
    val width = item.widthPx
    val height = item.heightPx
    return when {
        width != null && height != null -> Copy.infoResolution(width, height, item.megapixels())
        width != null -> Copy.infoWidthOnly(width)
        height != null -> Copy.infoHeightOnly(height)
        else -> Copy.INFO_UNKNOWN
    }
}

/**
 * The badge's date: "12 Aug", or "12 Aug 2019" once the year stops being obvious.
 *
 * The year is dropped for the current year and kept otherwise, which is the one case the
 * badge exists to catch. Almost everything in a gallery is recent, so printing the year on
 * every card spends four characters saying "this year" over and over — and then fails to
 * stand out on the one card from 2019, which is the card most worth binning.
 */
private fun shortDate(dateAddedSec: Long): String {
    val millis = dateAddedSec * 1000L
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val itemYear = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    val pattern = if (itemYear == thisYear) "d MMM" else "d MMM yyyy"
    return android.text.format.DateFormat.format(pattern, millis).toString()
}

/**
 * The sheet's date: the whole thing, including the time.
 *
 * The sheet is where precision belongs, and "Added" is a fact about a file rather than a
 * memory about a day — two photographs a minute apart are a burst, which is exactly the kind
 * of thing a user opens this sheet to confirm.
 */
private fun fullDateTime(dateAddedSec: Long): String {
    val millis = dateAddedSec * 1000L
    return android.text.format.DateFormat.format("d MMMM yyyy, HH:mm", millis).toString()
}


/** The glyph buttons on the sheet's title line. Smaller than a deck control — this is chrome. */
private val GlyphButton = 38.dp
private val GlyphIcon = 19.dp

/**
 * Share, with two gestures on one glyph.
 *
 * `combinedClickable` rather than a gesture detector, because both gestures have to reach a
 * screen reader: a long press that only exists as a raw pointer callback is a feature blind
 * users cannot find at all. This way the node carries two labelled actions.
 */
@Composable
private fun ShareGlyph(onShare: () -> Unit, onShareMany: () -> Unit) {
    val haptics = rememberSwipeyHaptics()
    val colors = SwipeyTheme.colors
    Box(
        Modifier
            .size(GlyphButton)
            .clip(CircleShape)
            .background(colors.surfaceStrong)
            .combinedClickable(
                onClickLabel = Copy.SHARE,
                onLongClickLabel = Copy.SHARE_HINT,
                onLongClick = {
                    // The press has already committed by the time this fires, and there is
                    // nothing on screen that moves to say so — hence the knock.
                    haptics.commit()
                    onShareMany()
                },
                onClick = onShare,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SwipeyIcon(
            SwipeyIcons.Share,
            contentDescription = Copy.SHARE,
            tint = colors.textPrimary,
            size = GlyphIcon,
        )
    }
}
