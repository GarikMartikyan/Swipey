package com.swipey.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.swipey.app.data.Settings
import com.swipey.app.data.ThemeChoice
import com.swipey.app.domain.BinSide
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRow
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySheet
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/** The row's glyph. Larger than the menu's, because here it is doing some of the explaining. */
private val RowIcon = 22.dp

/** Keeps a ticked option and an unticked one on the same text baseline. */
private val MarkerSize = 20.dp

/** Which setting's sheet is open, or none. */
private enum class Editing { THEME, BIN, SOUND }

/**
 * Everything the app remembers about itself: a palette, a swipe direction, and whether
 * clips arrive with their sound on.
 *
 * ### Three rows, and each one is a sentence
 * A row per setting, and under each one a line saying what is true right now — "Swipe left
 * to bin, right to keep", rewritten the moment it stops being true. No values in the right
 * margin. The conventional arrangement, a name and its value, works only for a reader who
 * already knows what the value *does*, and for this screen's middle setting that reader is
 * nobody: "Left" is not an answer to anything unless you already know the question.
 *
 * So the screen can be read without opening anything. Opening a row is for changing it, and
 * the sheet that opens describes both options for the same reason — the instant of choosing
 * between Left and Right is the instant that description is most wanted.
 *
 * ### The glyphs say which state, not which setting
 * The appearance row shows a sun or a moon depending on which palette is in force, and the
 * sound row a speaker plain or struck through. A neutral "appearance" mark would say only
 * that a theme setting exists — something the word beside it already says. See
 * [SwipeyIcons.Sun].
 *
 * ### Applied on the tap
 * Every choice takes effect immediately and there is no confirmation: the palette repaints
 * under the finger, the deck's next drag obeys the new direction, the sound setting reseeds
 * the run. A settings screen with an Apply button is one that can be left in a state the app
 * is not in.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onTheme: (ThemeChoice) -> Unit,
    onBinSide: (BinSide) -> Unit,
    onVideoSound: (Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    val dark = settings.theme == ThemeChoice.DARK
    val binOnLeft = settings.binSide == BinSide.LEFT

    SwipeyScreen(
        overlay = {
            // One sheet, told what it is about, rather than three sheets each held in their
            // own visibility flag: only one can ever be open, and three flags is three ways
            // for two to be.
            SwipeySheet(
                visible = editing != null,
                onDismiss = { editing = null },
                title = when (editing) {
                    Editing.THEME -> Copy.SETTINGS_APPEARANCE
                    Editing.BIN -> Copy.SETTINGS_BIN_SIDE
                    Editing.SOUND -> Copy.SETTINGS_VIDEO_SOUND
                    // Held through the exit animation: the sheet stays composed while it
                    // slides away, and a title that vanished on the first frame of that
                    // would read as the sheet breaking rather than closing.
                    null -> null
                },
            ) {
                when (editing) {
                    Editing.THEME -> {
                        Choice(
                            title = Copy.SETTINGS_THEME_LIGHT,
                            subtitle = Copy.SETTINGS_THEME_LIGHT_SUB,
                            selected = !dark,
                            onClick = { onTheme(ThemeChoice.LIGHT); editing = null },
                        )
                        Choice(
                            title = Copy.SETTINGS_THEME_DARK,
                            subtitle = Copy.SETTINGS_THEME_DARK_SUB,
                            selected = dark,
                            divider = false,
                            onClick = { onTheme(ThemeChoice.DARK); editing = null },
                        )
                    }
                    Editing.BIN -> {
                        Choice(
                            title = Copy.SETTINGS_SIDE_LEFT,
                            subtitle = Copy.SETTINGS_SIDE_LEFT_SUB,
                            selected = binOnLeft,
                            onClick = { onBinSide(BinSide.LEFT); editing = null },
                        )
                        Choice(
                            title = Copy.SETTINGS_SIDE_RIGHT,
                            subtitle = Copy.SETTINGS_SIDE_RIGHT_SUB,
                            selected = !binOnLeft,
                            divider = false,
                            onClick = { onBinSide(BinSide.RIGHT); editing = null },
                        )
                    }
                    Editing.SOUND -> {
                        Choice(
                            title = Copy.SETTINGS_ON,
                            subtitle = Copy.SETTINGS_ON_SUB,
                            selected = settings.videoSound,
                            onClick = { onVideoSound(true); editing = null },
                        )
                        Choice(
                            title = Copy.SETTINGS_OFF,
                            subtitle = Copy.SETTINGS_OFF_SUB,
                            selected = !settings.videoSound,
                            divider = false,
                            onClick = { onVideoSound(false); editing = null },
                        )
                    }
                    null -> Unit
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SwipeyText(
                Copy.SETTINGS_TITLE,
                modifier = Modifier.padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.lg),
                style = SwipeyTheme.typography.display,
                color = SwipeyTheme.colors.textPrimary,
            )

            SettingRow(
                icon = if (dark) SwipeyIcons.Moon else SwipeyIcons.Sun,
                title = Copy.SETTINGS_APPEARANCE,
                says = Copy.settingsThemeSays(dark),
                onClick = { editing = Editing.THEME },
            )
            SettingRow(
                icon = SwipeyIcons.SwapSides,
                title = Copy.SETTINGS_BIN_SIDE,
                says = Copy.settingsBinSays(binOnLeft),
                onClick = { editing = Editing.BIN },
            )
            SettingRow(
                icon = if (settings.videoSound) SwipeyIcons.SoundOn else SwipeyIcons.SoundOff,
                title = Copy.SETTINGS_VIDEO_SOUND,
                says = if (settings.videoSound) Copy.SETTINGS_VIDEO_SOUND_ON else Copy.SETTINGS_VIDEO_SOUND_OFF,
                onClick = { editing = Editing.SOUND },
                divider = false,
            )

            Box(Modifier.padding(bottom = SwipeySpacing.xxl))
        }
    }
}

/**
 * One setting: a glyph of its current state, its name, and a sentence about it.
 *
 * The glyph is decorative by construction — it restates the sentence beside it, and a screen
 * reader announcing "moon, Appearance, Dark…" would be reading the same fact twice.
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    says: String,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    SwipeyRow(
        title = title,
        subtitle = says,
        onClick = onClick,
        divider = divider,
        leading = {
            SwipeyIcon(
                icon,
                contentDescription = null,
                tint = SwipeyTheme.colors.textSecondary,
                size = RowIcon,
            )
        },
    )
}

/**
 * One option inside a sheet: a tick's worth of space, a name, and what choosing it means.
 *
 * The tick column is held open whether or not it is filled, exactly as `SortChooserScreen`
 * holds it, so both names start on the same line. No chevron: these pick a value rather than
 * lead anywhere.
 */
@Composable
private fun Choice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    SwipeyRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        showChevron = false,
        divider = divider,
        leading = {
            if (selected) {
                SwipeyIcon(
                    SwipeyIcons.Check,
                    contentDescription = Copy.SELECTED,
                    tint = SwipeyTheme.colors.keep,
                    size = MarkerSize,
                )
            } else {
                Box(Modifier.size(MarkerSize))
            }
        },
    )
}
