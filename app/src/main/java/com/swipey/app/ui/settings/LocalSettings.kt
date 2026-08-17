package com.swipey.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.swipey.app.data.Settings
import com.swipey.app.data.ThemeChoice
import com.swipey.app.domain.BinSide

/**
 * The settings in force, available anywhere in the tree.
 *
 * A [androidx.compose.runtime.CompositionLocal] rather than a parameter threaded down,
 * because the two readers that are not the Settings screen sit at the bottom of a deep
 * stack: `SwipeCard` needs the bin side, and `rememberVideoPlayback` needs the sound
 * default, and both are four or five composables below the root. Passing them by hand would
 * mean adding a parameter to every composable in between that has no use for it — which is
 * precisely the situation this mechanism exists for, and precisely how `SwipeyTheme` already
 * distributes the palette.
 *
 * The default is the same one [com.swipey.app.data.SettingsPreferences] falls back to, so a
 * `@Preview` or a component lifted out of the tree renders a coherent screen rather than
 * throwing.
 */
val LocalSettings = staticCompositionLocalOf {
    Settings(theme = ThemeChoice.DARK, binSide = BinSide.LEFT, videoSound = true)
}

/** Provides [settings] to [content]. Called once, at the root, beside `SwipeyTheme`. */
@Composable
fun ProvideSettings(settings: Settings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettings provides settings, content = content)
}

/** Which side bins, for the deck. Shorthand for the one field most callers want. */
val currentBinSide: BinSide
    @Composable @ReadOnlyComposable get() = LocalSettings.current.binSide
