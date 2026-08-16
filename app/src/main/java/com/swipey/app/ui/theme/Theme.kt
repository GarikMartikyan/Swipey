package com.swipey.app.ui.theme

import com.swipey.app.ui.design.SwipeyDarkColors

/**
 * What is left of the old Material theme.
 *
 * `SwipeyTheme` now lives in `ui/design/Tokens.kt` and wraps nothing in `MaterialTheme`;
 * `MainActivity` calls that one. These two colours survive only because
 * `ui/deck/SwipeCard.kt` still imports them for its swipe-direction tint, and that screen
 * is rebuilt in the next pass — deleting them now would break a file this change is not
 * meant to touch.
 *
 * They are no longer the old Material greens and reds: both forward to the design
 * system's accents, so the tint under a half-swiped card already matches the buttons
 * beneath it. Once `SwipeCard` moves to `SwipeyTheme.colors.keep` / `.bin`, delete this
 * file.
 */

@Deprecated(
    "Use SwipeyTheme.colors.keep",
    ReplaceWith("SwipeyTheme.colors.keep", "com.swipey.app.ui.design.SwipeyTheme"),
)
val KeepGreen = SwipeyDarkColors.keep

@Deprecated(
    "Use SwipeyTheme.colors.bin",
    ReplaceWith("SwipeyTheme.colors.bin", "com.swipey.app.ui.design.SwipeyTheme"),
)
val MarkRed = SwipeyDarkColors.bin
