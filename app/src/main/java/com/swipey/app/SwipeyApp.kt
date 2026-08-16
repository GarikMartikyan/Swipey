package com.swipey.app

import android.app.Application
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.TrashRepository
import com.swipey.app.data.db.SwipeyDatabase

class SwipeyApp : Application() {
    val database by lazy { SwipeyDatabase.get(this) }
    val mediaRepository by lazy { MediaRepository(contentResolver) }
    val trashRepository by lazy { TrashRepository(contentResolver, mediaRepository, database) }
}
