package com.swipey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swipey.app.ui.SwipeyRoot
import com.swipey.app.ui.design.SwipeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge to edge. Swipey's whole subject is the photograph on screen, and a
        // photograph that stops short of the physical edge to make room for two grey bars
        // is a smaller photograph for no reason. The bars stay transparent (themes.xml);
        // SwipeyScreen insets the *interface* out from under them, and the deck lets the
        // image itself run underneath.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val darkTheme = isSystemInDarkTheme()

            // Status- and navigation-bar icons are drawn by the system, not by us, so
            // they have to be told which way to go: light glyphs over Swipey's near-black
            // canvas, dark glyphs over the near-white one. Without this the clock is
            // invisible in one of the two themes.
            //
            // Keyed on `darkTheme` so it re-runs when the system flips at sunset, which
            // happens without an Activity recreation on some OEMs.
            DisposableEffect(darkTheme) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                onDispose { }
            }

            SwipeyTheme { SwipeyRoot(application as SwipeyApp) }
        }
    }
}
