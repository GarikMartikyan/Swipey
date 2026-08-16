package com.swipey.app.ui.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.domain.SortMode
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRow
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

private val labels = listOf(
    SortMode.NEWEST to Copy.SORT_NEWEST,
    SortMode.OLDEST to Copy.SORT_OLDEST,
    SortMode.LARGEST to Copy.SORT_LARGEST,
    SortMode.SMALLEST to Copy.SORT_SMALLEST,
)

/** Keeps the tick and the un-ticked rows on one text baseline. */
private val MarkerSize = 20.dp

/**
 * Which order the deck deals in. Four rows, one of them ticked.
 *
 * @param current the order that is in force. Defaulted, and defaulted to the same value
 *   `Routes.deck` uses when no sort is passed, so the existing single call site — which
 *   has no sort state of its own to hand over — keeps compiling and still shows a
 *   correct tick. A chooser with no visible current selection is a list of buttons, not
 *   a choice.
 */
@Composable
fun SortChooserScreen(onPick: (SortMode) -> Unit, current: SortMode = SortMode.NEWEST) {
    SwipeyScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SwipeyText(
                Copy.SORT_TITLE,
                modifier = Modifier.padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.lg),
                style = SwipeyTheme.typography.display,
                color = SwipeyTheme.colors.textPrimary,
            )

            labels.forEachIndexed { index, (mode, label) ->
                val selected = mode == current
                SwipeyRow(
                    title = label,
                    onClick = { onPick(mode) },
                    // These rows pick a value; they don't navigate onward to a screen
                    // the user can come back from, so a chevron would misdescribe them.
                    showChevron = false,
                    divider = index < labels.lastIndex,
                    leading = {
                        if (selected) {
                            SwipeyIcon(
                                SwipeyIcons.Check,
                                contentDescription = Copy.SELECTED,
                                tint = SwipeyTheme.colors.keep,
                                size = MarkerSize,
                            )
                        } else {
                            // Holds the tick's column open so the four labels line up.
                            Box(Modifier.size(MarkerSize))
                        }
                    },
                )
            }
        }
    }
}
