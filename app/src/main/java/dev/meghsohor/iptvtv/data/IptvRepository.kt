package dev.meghsohor.iptvtv.data

import androidx.room.withTransaction
import dev.meghsohor.iptvtv.data.db.BookmarkEntity
import dev.meghsohor.iptvtv.data.db.CategoryEntity
import dev.meghsohor.iptvtv.data.db.ChannelEntity
import dev.meghsohor.iptvtv.data.db.CountryEntity
import dev.meghsohor.iptvtv.data.db.IptvDatabase
import dev.meghsohor.iptvtv.data.db.StreamUrlEntity
import dev.meghsohor.iptvtv.data.remote.IptvOrgClient
import dev.meghsohor.iptvtv.data.remote.M3uEntry
import dev.meghsohor.iptvtv.data.remote.parseCsv
import dev.meghsohor.iptvtv.data.remote.parseM3u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Outcome of a [IptvRepository.refresh] — mirrors the "Refresh mechanism" section of the spec. */
data class RefreshResult(val added: Int, val removed: Int, val bookmarksRemoved: Int)

/** SQLite's default max bound parameters per statement (SQLITE_MAX_VARIABLE_NUMBER) — stay under it for `IN (:ids)` queries. */
private const val SqliteMaxBindVariables = 900

class IptvRepository(private val db: IptvDatabase, private val client: IptvOrgClient = IptvOrgClient()) {

  val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
  val countries: Flow<List<CountryEntity>> = db.countryDao().observeAll()
  val allChannels: Flow<List<ChannelEntity>> = db.channelDao().observeAll()
  val bookmarkedChannels: Flow<List<ChannelEntity>> = db.channelDao().observeBookmarked()

  fun channelsByCategory(categoryId: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCategory(categoryId)

  fun channelsByCountry(countryCode: String): Flow<List<ChannelEntity>> = db.channelDao().observeByCountry(countryCode)

  fun search(query: String): Flow<List<ChannelEntity>> = db.channelDao().observeSearch(query)

  fun channelById(channelId: String): Flow<ChannelEntity?> = db.channelDao().observeById(channelId)

  fun streamUrls(channelId: String): Flow<List<StreamUrlEntity>> = db.streamUrlDao().observeForChannel(channelId)

  fun isBookmarked(channelId: String): Flow<Boolean> = db.bookmarkDao().observeIsBookmarked(channelId)

  suspend fun addBookmark(channelId: String) = db.bookmarkDao().add(BookmarkEntity(channelId, System.currentTimeMillis()))

  suspend fun removeBookmark(channelId: String) = db.bookmarkDao().remove(channelId)

  suspend fun setSelectedSource(channelId: String, url: String?) = db.channelDao().setSelectedSourceUrl(channelId, url)

  /**
   * Full pipeline from the spec's "Refresh mechanism": fetch -> rebuild -> diff by `channel@feed`
   * -> preserve bookmarks/manual picks for survivors -> single transaction. If the fetch fails
   * outright (e.g. offline), this throws before anything is touched — never a partial overwrite.
   */
  private class BuiltChannels(
    val channels: List<ChannelEntity>,
    val urlsByChannel: Map<String, List<StreamUrlEntity>>,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
  )

  suspend fun refresh(): RefreshResult {
    val categoryCsv = client.fetchCategoriesCsv()
    val countryCsv = client.fetchCountriesCsv()
    val channelCsv = client.fetchChannelsCsv()

    val countryCodes = client.fetchPlaylistCountryCodes()
    val playlists = client.fetchAllPlaylists(countryCodes)
    check(playlists.isNotEmpty()) { "Refresh failed: could not reach iptv-org (no playlists fetched)" }

    val existingSelections = db.channelDao().allSelectedSources().associate { it.id to it.selectedSourceUrl }

    // CSV/M3U parsing and diff-building for ~11k channels is CPU-bound — keep it off the caller's
    // (usually Main) dispatcher so the UI stays responsive while a refresh runs.
    val built =
      withContext(Dispatchers.Default) {
        val categoryRows = parseCsv(categoryCsv)
        val countryRows = parseCsv(countryCsv)
        val channelRows = parseCsv(channelCsv).associateBy { it.getValue("id") }

        // First-seen order preserved across all playlists combined -> "list order = source order".
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
          val channelRow = channelRows[channelId] ?: continue // not in the metadata database, skip
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
        BuiltChannels(
          channels = newChannels,
          urlsByChannel = urlsByChannel,
          categories = categoryRows.mapIndexed { i, r -> CategoryEntity(r.getValue("id"), r.getValue("name"), i) },
          countries = countryRows.mapIndexed { i, r -> CountryEntity(r.getValue("code"), r.getValue("name"), r.getValue("flag"), i) },
        )
      }

    val existingIds = existingSelections.keys
    val newIds = built.channels.map { it.id }.toSet()
    val removedIds = (existingIds - newIds).toList()
    val addedCount = (newIds - existingIds).size
    val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()
    val bookmarksRemovedCount = removedIds.count { it in bookmarkedIds }

    db.withTransaction {
      db.categoryDao().replaceAll(built.categories)
      db.countryDao().replaceAll(built.countries)
      // Chunked: Room expands `WHERE id IN (:ids)` to one bind variable per id, and a removal
      // batch of 999+ channels (e.g. a big iptv-org cleanup) would exceed SQLite's default limit.
      for (chunk in removedIds.chunked(SqliteMaxBindVariables)) db.channelDao().deleteByIds(chunk) // cascades to stream_urls + bookmarks
      db.channelDao().upsertAll(built.channels)
      db.streamUrlDao().replaceAll(built.urlsByChannel)
    }

    return RefreshResult(added = addedCount, removed = removedIds.size, bookmarksRemoved = bookmarksRemovedCount)
  }
}
