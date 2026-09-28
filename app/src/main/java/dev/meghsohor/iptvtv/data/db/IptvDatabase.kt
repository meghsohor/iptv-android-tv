package dev.meghsohor.iptvtv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities =
    [CategoryEntity::class, CountryEntity::class, ChannelEntity::class, StreamUrlEntity::class, BookmarkEntity::class, FailedChannelEntity::class, DeletedChannelEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class IptvDatabase : RoomDatabase() {
  abstract fun categoryDao(): CategoryDao

  abstract fun countryDao(): CountryDao

  abstract fun channelDao(): ChannelDao

  abstract fun streamUrlDao(): StreamUrlDao

  abstract fun bookmarkDao(): BookmarkDao

  abstract fun failedChannelDao(): FailedChannelDao

  abstract fun deletedChannelDao(): DeletedChannelDao

  companion object {
    @Volatile private var instance: IptvDatabase? = null

    private val Migration1To2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("CREATE TABLE IF NOT EXISTS `failed_channels` (`channelId` TEXT NOT NULL, `failedAt` INTEGER NOT NULL, PRIMARY KEY(`channelId`))")
          db.execSQL("CREATE TABLE IF NOT EXISTS `deleted_channels` (`channelId` TEXT NOT NULL, PRIMARY KEY(`channelId`))")
        }
      }

    fun getInstance(context: Context): IptvDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(context.applicationContext, IptvDatabase::class.java, "iptv.db").addMigrations(Migration1To2).build().also {
              instance = it
            }
        }
  }
}
