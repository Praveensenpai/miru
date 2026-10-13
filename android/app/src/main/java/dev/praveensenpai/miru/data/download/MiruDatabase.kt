package dev.praveensenpai.miru.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.praveensenpai.miru.data.library.HistoryEntity
import dev.praveensenpai.miru.data.library.LibraryDao
import dev.praveensenpai.miru.data.library.SubscriptionEntity

/**
 * Local database.
 *
 * v1 persisted downloads only. v2 adds subscriptions and watch history — the
 * Subscriptions and Library tabs read straight from here, and the player writes
 * history as videos play. One store, one migration path.
 */
@Database(
    entities = [
        DownloadEntity::class,
        SubscriptionEntity::class,
        HistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MiruDatabase : RoomDatabase() {

    abstract fun downloads(): DownloadDao

    abstract fun library(): LibraryDao

    companion object {
        @Volatile
        private var instance: MiruDatabase? = null

        /** v1 -> v2: add the subscriptions and history tables. Downloads are untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                        "`channelId` TEXT NOT NULL PRIMARY KEY, " +
                        "`name` TEXT NOT NULL, " +
                        "`avatar` TEXT, " +
                        "`subscribedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`videoId` TEXT NOT NULL PRIMARY KEY, " +
                        "`title` TEXT NOT NULL, " +
                        "`channel` TEXT, " +
                        "`channelId` TEXT, " +
                        "`thumbnail` TEXT, " +
                        "`duration` INTEGER, " +
                        "`watchedAt` INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): MiruDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MiruDatabase::class.java,
                    "miru.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
