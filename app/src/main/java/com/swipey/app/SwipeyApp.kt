package com.swipey.app

import android.app.Application
import com.swipey.app.data.HomePreferences
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.SettingsPreferences
import com.swipey.app.data.TrashRepository
import com.swipey.app.data.db.SwipeyDatabase

class SwipeyApp : Application() {
    val database by lazy { SwipeyDatabase.get(this) }
    val mediaRepository by lazy { MediaRepository(contentResolver) }
    val trashRepository by lazy { TrashRepository(this, contentResolver, mediaRepository, database) }
    val homePreferences by lazy { HomePreferences(this) }

    /**
     * Touched first by `MainActivity`, before the first composition, because the palette it
     * holds decides what the first frame looks like. See [SettingsPreferences] for why that
     * one read is allowed to be synchronous when the rest of the app's preferences are not.
     */
    val settingsPreferences by lazy { SettingsPreferences(this) }
}
