package dev.meghsohor.iptvtv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [CategoryEntity::class, CountryEntity::class, ChannelEntity::class, StreamUrlEntity::class, BookmarkEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class IptvDatabase : RoomDatabase() {
  abstract fun categoryDao(): CategoryDao

  abstract fun countryDao(): CountryDao

  abstract fun channelDao(): ChannelDao

  abstract fun streamUrlDao(): StreamUrlDao

  abstract fun bookmarkDao(): BookmarkDao

  companion object {
    @Volatile private var instance: IptvDatabase? = null

    fun getInstance(context: Context): IptvDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(context.applicationContext, IptvDatabase::class.java, "iptv.db").build().also {
              instance = it
            }
        }
  }
}
