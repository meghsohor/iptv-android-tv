package dev.meghsohor.iptvtv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories ORDER BY sortOrder") fun observeAll(): Flow<List<CategoryEntity>>

  @Query("DELETE FROM categories") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(categories: List<CategoryEntity>)

  @Transaction
  suspend fun replaceAll(categories: List<CategoryEntity>) {
    deleteAll()
    insertAll(categories)
  }
}

@Dao
interface CountryDao {
  @Query("SELECT * FROM countries ORDER BY sortOrder") fun observeAll(): Flow<List<CountryEntity>>

  @Query("DELETE FROM countries") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(countries: List<CountryEntity>)

  @Transaction
  suspend fun replaceAll(countries: List<CountryEntity>) {
    deleteAll()
    insertAll(countries)
  }
}

data class ChannelSelection(val id: String, val selectedSourceUrl: String?)

@Dao
interface ChannelDao {
  @Query("SELECT * FROM channels ORDER BY sortOrder") fun observeAll(): Flow<List<ChannelEntity>>

  @Query(
    "SELECT * FROM channels WHERE (';' || categoryIds || ';') LIKE ('%;' || :categoryId || ';%') ORDER BY sortOrder"
  )
  fun observeByCategory(categoryId: String): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM channels WHERE countryCode = :countryCode ORDER BY sortOrder")
  fun observeByCountry(countryCode: String): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM channels WHERE displayName LIKE '%' || :query || '%' ORDER BY sortOrder")
  fun observeSearch(query: String): Flow<List<ChannelEntity>>

  @Query(
    "SELECT channels.* FROM channels INNER JOIN bookmarks ON channels.id = bookmarks.channelId ORDER BY bookmarks.addedAt DESC"
  )
  fun observeBookmarked(): Flow<List<ChannelEntity>>

  @Query("SELECT id FROM channels") suspend fun allIds(): List<String>

  @Query("SELECT * FROM channels WHERE id = :id") suspend fun getById(id: String): ChannelEntity?

  @Query("SELECT * FROM channels WHERE id = :id") fun observeById(id: String): Flow<ChannelEntity?>

  /** id -> selectedSourceUrl for every channel, fetched in one query (no N+1) so a refresh over
   * thousands of channels can preserve manual Source picks without a per-channel round trip. */
  @Query("SELECT id, selectedSourceUrl FROM channels") suspend fun allSelectedSources(): List<ChannelSelection>

  // @Upsert, not @Insert(REPLACE): REPLACE is a SQLite DELETE-then-INSERT under the hood, which
  // would fire bookmarks' ON DELETE CASCADE for every surviving channel on every single refresh.
  @Upsert suspend fun upsertAll(channels: List<ChannelEntity>)

  @Query("DELETE FROM channels WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)

  @Query("UPDATE channels SET selectedSourceUrl = :url WHERE id = :channelId")
  suspend fun setSelectedSourceUrl(channelId: String, url: String?)
}

@Dao
interface StreamUrlDao {
  @Query("SELECT * FROM stream_urls WHERE channelId = :channelId ORDER BY sortOrder")
  fun observeForChannel(channelId: String): Flow<List<StreamUrlEntity>>

  @Query("SELECT * FROM stream_urls WHERE channelId = :channelId ORDER BY sortOrder")
  suspend fun getForChannel(channelId: String): List<StreamUrlEntity>

  @Query("DELETE FROM stream_urls") suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(urls: List<StreamUrlEntity>)

  /**
   * Replaces the entire table in one go. A refresh rebuilds [urlsByChannel] for essentially the
   * whole catalog already (every channel found in the freshly-fetched playlists), and channels
   * that disappeared are cascade-deleted separately — so a per-channel `WHERE channelId IN (...)`
   * delete isn't just redundant, it's unsafe: with ~11k channels it blows past SQLite's default
   * 999-bind-variable limit and crashes every refresh.
   */
  @Transaction
  suspend fun replaceAll(urlsByChannel: Map<String, List<StreamUrlEntity>>) {
    deleteAll()
    insertAll(urlsByChannel.values.flatten())
  }
}

@Dao
interface BookmarkDao {
  @Query("SELECT channelId FROM bookmarks") suspend fun allChannelIds(): List<String>

  @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE channelId = :channelId)")
  fun observeIsBookmarked(channelId: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(bookmark: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE channelId = :channelId") suspend fun remove(channelId: String)
}
