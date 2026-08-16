package com.swipey.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReviewedMediaEntity::class, TrashedItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SwipeyDatabase : RoomDatabase() {
    abstract fun reviewed(): ReviewedMediaDao
    abstract fun trashed(): TrashedItemDao

    companion object {
        @Volatile private var instance: SwipeyDatabase? = null

        fun get(context: Context): SwipeyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SwipeyDatabase::class.java,
                "swipey.db",
            ).build().also { instance = it }
        }
    }
}
