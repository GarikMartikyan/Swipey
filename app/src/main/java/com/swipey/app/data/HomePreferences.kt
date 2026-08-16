package com.swipey.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * The one thing Home remembers between launches: whether its Albums section is drawn as
 * rows or as tiles.
 *
 * Deliberately [android.content.SharedPreferences] and not a Room table. `SwipeyDatabase`
 * holds records that the app's recoverability guarantee depends on — which photos were
 * kept, which are pending in the system trash — and every one of them is reconciled
 * against MediaStore on launch. A layout toggle has nothing to reconcile and no
 * consequence if it is lost, and putting it there would mean a schema migration, a DAO and
 * a place for a genuinely important query to go wrong, all to store a boolean.
 *
 * The read is a blocking disk load on first touch, so callers do it off the main thread —
 * see `HomeViewModel`. Writes go through `apply()`, which is in-memory immediately and on
 * disk shortly after; a toggle lost to a kill in that window costs the user one tap.
 */
class HomePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Rows are the default: they carry the size, which is the reason to open an album. */
    var albumsAsGrid: Boolean
        get() = prefs.getBoolean(KEY_ALBUMS_AS_GRID, false)
        set(value) = prefs.edit { putBoolean(KEY_ALBUMS_AS_GRID, value) }

    private companion object {
        const val FILE = "swipey.home"
        const val KEY_ALBUMS_AS_GRID = "albums_as_grid"
    }
}
