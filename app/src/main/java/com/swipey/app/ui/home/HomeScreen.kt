package com.swipey.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyDivider
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRow
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/**
 * The front door: the wordmark, three ways into the deck, and the Bin.
 *
 * Three tall rows separated by hairlines rather than three cards — cards would put a
 * boxed edge around each choice and make the screen look busier than it is, and the
 * whole app's list rhythm is [SwipeyRow] anyway.
 *
 * The Bin is deliberately not a fourth entry in that run. It is the only row here that
 * doesn't start a session, so it sits below the scroll as a footer, behind its own rule,
 * with the Bin glyph and its live count.
 */
@Composable
fun HomeScreen(
    binCount: Int,
    onAllMedia: () -> Unit,
    onAlbums: () -> Unit,
    onShuffle: () -> Unit,
    onBin: () -> Unit,
) {
    SwipeyScreen {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    // At a 200% font scale the wordmark and three rows are taller than
                    // the screen; the footer below stays put while this scrolls.
                    .verticalScroll(rememberScrollState()),
            ) {
                SwipeyText(
                    Copy.APP_NAME,
                    modifier = Modifier.padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.xl),
                    style = SwipeyTheme.typography.display,
                    color = SwipeyTheme.colors.textPrimary,
                )

                SwipeyRow(
                    title = Copy.HOME_ALL_MEDIA,
                    subtitle = Copy.HOME_ALL_MEDIA_SUB,
                    onClick = onAllMedia,
                )
                SwipeyRow(
                    title = Copy.HOME_ALBUMS,
                    subtitle = Copy.HOME_ALBUMS_SUB,
                    onClick = onAlbums,
                )
                SwipeyRow(
                    title = Copy.HOME_SHUFFLE,
                    subtitle = Copy.HOME_SHUFFLE_SUB,
                    onClick = onShuffle,
                    // The run of session entries ends here; the footer draws its own rule.
                    divider = false,
                )
            }

            Column(Modifier.fillMaxWidth().padding(bottom = SwipeySpacing.sm)) {
                SwipeyDivider()
                SwipeyRow(
                    title = Copy.HOME_BIN,
                    trailing = Copy.homeBinSubtitle(binCount),
                    onClick = onBin,
                    divider = false,
                    leading = {
                        SwipeyIcon(
                            SwipeyIcons.Bin,
                            // Decorative: the row is titled "Bin" right beside it.
                            contentDescription = null,
                            tint = SwipeyTheme.colors.textSecondary,
                            size = 20.dp,
                        )
                    },
                )
            }
        }
    }
}
