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
import kotlinx.coroutines.flow.Flow

/** Outcome of a [IptvRepository.refresh] — mirrors the "Refresh mechanism" section of the spec. */
data class RefreshResult(val added: Int, val removed: Int, val bookmarksRemoved: Int)

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
  suspend fun refresh(): RefreshResult {
    val categoryRows = parseCsv(client.fetchCategoriesCsv())
    val countryRows = parseCsv(client.fetchCountriesCsv())
    val channelRows = parseCsv(client.fetchChannelsCsv()).associateBy { it.getValue("id") }

    val countryCodes = client.fetchPlaylistCountryCodes()
    val playlists = client.fetchAllPlaylists(countryCodes)
    check(playlists.isNotEmpty()) { "Refresh failed: could not reach iptv-org (no playlists fetched)" }

    // First-seen order preserved across all playlists combined -> "list order = source order".
    val entriesByKey = LinkedHashMap<String, MutableList<M3uEntry>>()
    for ((_, text) in playlists) {
      for (entry in parseM3u(text)) {
        entriesByKey.getOrPut(entry.tvgId) { mutableListOf() }.add(entry)
      }
    }

    val existingSelections = db.channelDao().allSelectedSources().associate { it.id to it.selectedSourceUrl }
    val existingIds = existingSelections.keys

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

    val newIds = newChannels.map { it.id }.toSet()
    val removedIds = (existingIds - newIds).toList()
    val addedCount = (newIds - existingIds).size
    val bookmarkedIds = db.bookmarkDao().allChannelIds().toSet()
    val bookmarksRemovedCount = removedIds.count { it in bookmarkedIds }

    db.withTransaction {
      db.categoryDao().replaceAll(categoryRows.mapIndexed { i, r -> CategoryEntity(r.getValue("id"), r.getValue("name"), i) })
      db.countryDao()
        .replaceAll(countryRows.mapIndexed { i, r -> CountryEntity(r.getValue("code"), r.getValue("name"), r.getValue("flag"), i) })
      db.channelDao().deleteByIds(removedIds) // cascades to stream_urls + bookmarks
      db.channelDao().upsertAll(newChannels)
      for ((channelId, urls) in urlsByChannel) {
        db.streamUrlDao().replaceForChannel(channelId, urls)
      }
    }

    return RefreshResult(added = addedCount, removed = removedIds.size, bookmarksRemoved = bookmarksRemovedCount)
  }
}
