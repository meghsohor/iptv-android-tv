package dev.meghsohor.iptvtv.data

import androidx.room.withTransaction
import dev.meghsohor.iptvtv.data.db.BookmarkEntity
import dev.meghsohor.iptvtv.data.db.CategoryEntity
import dev.meghsohor.iptvtv.data.db.ChannelEntity
import dev.meghsohor.iptvtv.data.db.CountryEntity
import dev.meghsohor.iptvtv.data.db.DeletedChannelEntity
import dev.meghsohor.iptvtv.data.db.FailedChannelEntity
import dev.meghsohor.iptvtv.data.db.IptvDatabase
import dev.meghsohor.iptvtv.data.db.StreamUrlEntity
import dev.meghsohor.iptvtv.data.remote.IptvOrgClient
import dev.meghsohor.iptvtv.data.remote.M3uEntry
import dev.meghsohor.iptvtv.data.remote.parseCsv
import dev.meghsohor.iptvtv.data.remote.parseM3u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RefreshResult(val added: Int, val removed: Int, val bookmarksRemoved: Int)

// Under SQLite's default limit of 999 bound parameters per statement.
private const val SqliteMaxBindVariables = 900

class IptvRepository(private val db: IptvDatabase, private val client: IptvOrgClient = IptvOrgClient()) {

  val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
  val countries: Flow<List<CountryEntity>> = db.countryDao().observeAll()
  val allChannels: Flow<List<ChannelEntity>> = db.channelDao().observeAll()
  val bookmarkedChannels: Flow<List<ChannelEntity>> = db.channelDao().observeBookmarked()

  suspend fun allChannelIds(): List<String> = db.channelDao().allIds()

  suspend fun hasChannels(): Boolean = db.channelDao().hasAny()

  // What refresh does, for data stored before it did.
  suspend fun pruneEmptyMenus() =
    db.withTransaction {
      db.categoryDao().deleteUnused()
      db.countryDao().deleteUnused()
    }

  fun channelsByCategory(categoryId: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCategory(categoryId)

  fun channelsByCountry(countryCode: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCountry(countryCode)

  fun search(query: String): Flow<List<ChannelEntity>> = db.channelDao().observeSearch(query)

  fun channelById(channelId: String): Flow<ChannelEntity?> = db.channelDao().observeById(channelId)

  fun streamUrls(channelId: String): Flow<List<StreamUrlEntity>> = db.streamUrlDao().observeForChannel(channelId)

  fun isBookmarked(channelId: String): Flow<Boolean> = db.bookmarkDao().observeIsBookmarked(channelId)

  suspend fun addBookmark(channelId: String) = db.bookmarkDao().add(BookmarkEntity(channelId, System.currentTimeMillis()))

  suspend fun removeBookmark(channelId: String) = db.bookmarkDao().remove(channelId)

  val failedChannelIds: Flow<List<String>> = db.failedChannelDao().observeIds()

  suspend fun markFailed(channelId: String) = db.failedChannelDao().add(FailedChannelEntity(channelId, System.currentTimeMillis()))

  suspend fun clearFailed(channelId: String) = db.failedChannelDao().remove(channelId)

  suspend fun deleteChannel(channelId: String) = db.deletedChannelDao().add(DeletedChannelEntity(channelId))

  suspend fun setSelectedSource(channelId: String, url: String?) = db.channelDao().setSelectedSourceUrl(channelId, url)

  private class BuiltChannels(
    val channels: List<ChannelEntity>,
    val urlsByChannel: Map<String, List<StreamUrlEntity>>,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
  )

  // Everything is fetched before the database is touched, so a failed fetch leaves the old data intact.
  suspend fun refresh(): RefreshResult {
    val categoryCsv = client.fetchCategoriesCsv()
    val countryCsv = client.fetchCountriesCsv()
    val channelCsv = client.fetchChannelsCsv()

    // Per-country files keep each channel's mirror URLs, but need the GitHub API to list them, which is
    // rate-limited per IP (403 on shared mobile networks). Fall back to the combined playlist, which needs no API.
    val playlists =
      runCatching { client.fetchAllPlaylists(client.fetchPlaylistCountryCodes()).ifEmpty { error("no playlists") } }
        .getOrElse { listOf("combined" to client.fetchCombinedPlaylist()) }
        .filter { it.second.isNotBlank() }
    check(playlists.isNotEmpty()) { "could not reach iptv-org (no playlists fetched)" }

    val existingSelections = db.channelDao().allSelectedSources().associate { it.id to it.selectedSourceUrl }

    // CPU-bound for ~11k channels: off the main thread.
    val built =
      withContext(Dispatchers.Default) {
        val categoryRows = parseCsv(categoryCsv)
        val countryRows = parseCsv(countryCsv)
        val channelRows = parseCsv(channelCsv).associateBy { it.getValue("id") }

        val entriesByKey = LinkedHashMap<String, MutableList<M3uEntry>>()
        for ((_, text) in playlists) {
          for (entry in parseM3u(text)) {
            entriesByKey.getOrPut(entry.tvgId) { mutableListOf() }.add(entry)
          }
        }

        var order = 0
        val newChannels = mutableListOf<ChannelEntity>()
        val urlsByChannel = mutableMapOf<String, List<StreamUrlEntity>>()
        for ((tvgId, entries) in entriesByKey) {
          val channelId = tvgId.substringBefore('@')
          val channelRow = channelRows[channelId] ?: continue
          val urls = entries.map { it.url }
          val preservedSelection = existingSelections[tvgId]?.takeIf { it in urls }
          newChannels +=
            ChannelEntity(
              id = tvgId,
              displayName = entries.first().title.ifBlank { channelRow["name"].orEmpty() },
              countryCode = channelRow["country"].orEmpty(),
              categoryIds = channelRow["categories"].orEmpty(),
              sortOrder = order++,
              selectedSourceUrl = preservedSelection,
            )
          urlsByChannel[tvgId] = entries.mapIndexed { idx, e -> StreamUrlEntity(channelId = tvgId, url = e.url, sortOrder = idx) }
        }
        // iptv-org defines categories and countries its playlists have no stream for ("XXX", Antarctica).
        val usedCategoryIds = newChannels.flatMapTo(HashSet()) { it.categoryIds.split(';') }
        val usedCountryCodes = newChannels.mapTo(HashSet()) { it.countryCode }
        BuiltChannels(
          channels = newChannels,
          urlsByChannel = urlsByChannel,
          categories =
            categoryRows
              .filter { it["id"] in usedCategoryIds }
              .mapIndexed { i, r -> CategoryEntity(r.getValue("id"), r.getValue("name"), i) },
          countries =
            countryRows
              .filter { it["code"] in usedCountryCodes }
              .mapIndexed { i, r -> CountryEntity(r.getValue("code"), r.getValue("name"), r.getValue("flag"), i) },
        )
      }

    // A 200 with a non-playlist body (captive portal, error page) parses to nothing; writing that would delete
    // every channel and, by cascade, every favourite. Keep the old catalogue instead.
    check(built.channels.isNotEmpty()) { "iptv-org returned no usable channels" }

    val existingIds = existingSelections.keys
    val newIds = built.channels.map { it.id }.toSet()
    val removedIds = (existingIds - newIds).toList()
    val addedCount = (newIds - existingIds).size
    val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()
    val bookmarksRemovedCount = removedIds.count { it in bookmarkedIds }

    db.withTransaction {
      db.categoryDao().replaceAll(built.categories)
      db.countryDao().replaceAll(built.countries)
      // Chunked: `IN (:ids)` binds one parameter per id. Cascades to stream_urls and bookmarks.
      for (chunk in removedIds.chunked(SqliteMaxBindVariables)) db.channelDao().deleteByIds(chunk)
      db.channelDao().upsertAll(built.channels)
      db.streamUrlDao().replaceAll(built.urlsByChannel)
      db.deletedChannelDao().clear()
      db.failedChannelDao().deleteOrphans()
    }

    return RefreshResult(added = addedCount, removed = removedIds.size, bookmarksRemoved = bookmarksRemovedCount)
  }
}
