package com.swipey.app

import android.app.Application
import com.swipey.app.data.db.SwipeyDatabase

class SwipeyApp : Application() {
    val database by lazy { SwipeyDatabase.get(this) }
}
