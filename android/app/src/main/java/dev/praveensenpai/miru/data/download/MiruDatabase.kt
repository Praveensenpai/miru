package dev.praveensenpai.miru.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database.
 *
 * v1 only persists downloads. Subscriptions, history, and playlists reuse the
 * same database in v2 rather than introducing a second store.
 */
@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class MiruDatabase : RoomDatabase() {

    abstract fun downloads(): DownloadDao

    companion object {
        @Volatile
        private var instance: MiruDatabase? = null

        fun get(context: Context): MiruDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MiruDatabase::class.java,
                    "miru.db",
                ).build().also { instance = it }
            }
    }
}