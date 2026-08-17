package com.swipey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipey.app.data.ThemeChoice
import com.swipey.app.ui.SwipeyRoot
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.settings.ProvideSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge to edge. Swipey's whole subject is the photograph on screen, and a
        // photograph that stops short of the physical edge to make room for two grey bars
        // is a smaller photograph for no reason. The bars stay transparent (themes.xml);
        // SwipeyScreen insets the *interface* out from under them, and the deck lets the
        // image itself run underneath.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as SwipeyApp

        setContent {
            // The user's palette, not the system's. `SettingsPreferences` resolves an
            // unmade choice to whatever the phone is set to, so an install that has never
            // opened Settings behaves exactly as this line did when it read
            // `isSystemInDarkTheme()` — and one that has, does not get overruled at sunset.
            val settings by app.settingsPreferences.state.collectAsStateWithLifecycle()
            val darkTheme = settings.theme == ThemeChoice.DARK

            // Status- and navigation-bar icons are drawn by the system, not by us, so
            // they have to be told which way to go: light glyphs over Swipey's near-black
            // canvas, dark glyphs over the near-white one. Without this the clock is
            // invisible in one of the two themes.
            //
            // Keyed on `darkTheme` so it re-runs the moment the setting changes — and, for
            // an install still following the phone, when the system flips at sunset without
            // an Activity recreation, which some OEMs do.
            DisposableEffect(darkTheme) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                onDispose { }
            }

            // Settings sit outside the theme rather than inside it: the theme is one of the
            // things they decide, and the deck re-wraps `SwipeyTheme` in the dark palette in
            // several places — none of which should be able to lose the swipe direction on
            // the way past.
            ProvideSettings(settings) {
                SwipeyTheme(darkTheme = darkTheme) { SwipeyRoot(app) }
            }
        }
    }
}
