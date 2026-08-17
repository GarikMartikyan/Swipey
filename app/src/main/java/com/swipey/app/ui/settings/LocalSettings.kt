package com.swipey.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.swipey.app.data.Settings
import com.swipey.app.data.ThemeChoice
import com.swipey.app.domain.BinSide
import com.swipey.app.ui.design.LocalSwipeyHapticsEnabled

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
    Settings(theme = ThemeChoice.DARK, binSide = BinSide.LEFT, videoSound = true, haptics = true)
}

/**
 * Provides [settings] to [content]. Called once, at the root, beside `SwipeyTheme`.
 *
 * Haptics are handed on separately, into the design system's own
 * [LocalSwipeyHapticsEnabled]. That keeps the dependency pointing one way: `ui/design`
 * knows a flag exists and never learns where it came from, and `rememberSwipeyHaptics`
 * stays the one place in the app that decides whether a thumb gets an answer.
 */
@Composable
fun ProvideSettings(settings: Settings, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalSwipeyHapticsEnabled provides settings.haptics,
        content = content,
    )
}

/** Which side bins, for the deck. Shorthand for the one field most callers want. */
val currentBinSide: BinSide
    @Composable @ReadOnlyComposable get() = LocalSettings.current.binSide
