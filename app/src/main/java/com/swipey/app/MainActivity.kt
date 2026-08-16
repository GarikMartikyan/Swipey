package com.swipey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.swipey.app.ui.SwipeyRoot
import com.swipey.app.ui.theme.SwipeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwipeyTheme { SwipeyRoot(application as SwipeyApp) }
        }
    }
}
