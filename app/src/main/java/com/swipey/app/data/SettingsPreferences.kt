package com.swipey.app.data

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import com.swipey.app.domain.BinSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which palette the app draws in. There is no "System" — see [SettingsPreferences.theme]. */
enum class ThemeChoice { LIGHT, DARK }

/**
 * Everything the Settings screen decides, as one value.
 *
 * One object rather than three flows because the screen shows all three at once and the app
 * reads them from one [androidx.compose.runtime.CompositionLocal]: three separate flows would
 * be three separate recomposition triggers for a value that only ever changes one field at a
 * time anyway.
 *
 * @property theme the palette in force. Already resolved — a null stored choice has been
 *   turned into whatever the phone is set to by the time it reaches here.
 * @property binSide which side of the deck sends a photograph to the bin.
 * @property videoSound whether a clip starts with its sound on. A *starting* value only: what
 *   the user does with the sound button afterwards is theirs for the rest of the run, and is
 *   deliberately not written back here. See `VideoSound`.
 */
data class Settings(
    val theme: ThemeChoice,
    val binSide: BinSide,
    val videoSound: Boolean,
)

/**
 * The three choices the app remembers about itself, and the only place they are written.
 *
 * ### Why this is read on the main thread, when `HomePreferences` is not
 * [HomePreferences] hops to IO for every touch, because a bookmark and a layout toggle are
 * wanted *after* the first frame and blocking on a file parse before it would be paying for
 * nothing. The theme is the opposite case: it decides what the very first frame looks like.
 * Read it late and the app opens in the wrong palette and corrects itself a frame later,
 * which is a flash of white on a dark phone — the single most visible bug a theme setting can
 * have. So this one file is parsed synchronously, once, on the way to the first composition.
 * It holds three keys.
 *
 * Deliberately a second file rather than three more keys in `swipey.home`. That file is
 * written on **every swipe** (the resume bookmark), and settings are written a handful of
 * times in an install's life; keeping them apart means a settings read can never be waiting
 * on a write from the middle of a session, and the two have nothing to do with each other.
 *
 * @param context used once, for the file and for the phone's current dark-mode state. Not
 *   retained.
 */
class SettingsPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * What the phone itself is set to, read once at construction.
     *
     * The seed for [theme] until the user makes a choice of their own. Read here rather than
     * from `isSystemInDarkTheme()` at the point of use because this class has no composition
     * to live in, and because it must be answerable before the first frame.
     */
    private val systemIsDark: Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private val _state = MutableStateFlow(read())

    /** The current settings. Collected at the root and provided to the whole tree. */
    val state: StateFlow<Settings> = _state

    val current: Settings get() = _state.value

    /**
     * Pins the palette.
     *
     * There is no way back to "follow the phone" once this has been called, and that is the
     * choice the screen offers: two rows, Light and Dark, because a third row for a state
     * that is *also* the default reads as an extra decision rather than as the absence of
     * one. Until the first call, [read] answers with the phone's own setting — so an install
     * that never opens this screen behaves exactly as the app did before the screen existed,
     * sunset flip included.
     */
    fun setTheme(choice: ThemeChoice) = write { putString(KEY_THEME, choice.name) }

    fun setBinSide(side: BinSide) = write { putString(KEY_BIN_SIDE, side.name) }

    fun setVideoSound(on: Boolean) = write { putBoolean(KEY_VIDEO_SOUND, on) }

    /**
     * `commit()`, not `apply()`, and it is the difference that matters here.
     *
     * These are written a few times ever, from a tap the user is watching the result of, so
     * the cost of a synchronous write is invisible — and losing one to a kill would mean the
     * app reopening in the palette they just changed away from, which is exactly the kind of
     * "it didn't save" that makes a settings screen untrustworthy. The bookmark in
     * `HomePreferences` takes the opposite trade for the opposite reason.
     */
    private inline fun write(crossinline edits: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true) { edits() }
        _state.value = read()
    }

    private fun read() = Settings(
        theme = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } }
            ?: if (systemIsDark) ThemeChoice.DARK else ThemeChoice.LIGHT,
        binSide = prefs.getString(KEY_BIN_SIDE, null)
            ?.let { name -> BinSide.entries.firstOrNull { it.name == name } }
            ?: BinSide.LEFT,
        // On out of the box. The point of the setting is to hear the clips; shipping it off
        // would make the feature look broken until you found the switch that turns it on.
        videoSound = prefs.getBoolean(KEY_VIDEO_SOUND, true),
    )

    private companion object {
        const val FILE = "swipey.settings"
        const val KEY_THEME = "theme"
        const val KEY_BIN_SIDE = "bin_side"
        const val KEY_VIDEO_SOUND = "video_sound"
    }
}
