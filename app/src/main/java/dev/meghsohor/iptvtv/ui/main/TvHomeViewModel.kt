package dev.meghsohor.iptvtv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.meghsohor.iptvtv.data.IptvRepository
import dev.meghsohor.iptvtv.data.db.CategoryEntity
import dev.meghsohor.iptvtv.data.db.ChannelEntity
import dev.meghsohor.iptvtv.data.db.CountryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TvHomeUiState(
  val panel: PanelState = PanelState.CategoriesMenu,
  val categories: List<CategoryEntity> = emptyList(),
  val countries: List<CountryEntity> = emptyList(),
  val listChannels: List<ChannelEntity> = emptyList(),
  val bookmarkedIds: Set<String> = emptySet(),
  val currentChannel: ChannelEntity? = null,
  val currentStreamUrls: List<String> = emptyList(),
  val searchQuery: String = "",
  val refreshing: Boolean = false,
  val refreshMessage: String? = null,
)

class TvHomeViewModel(private val repository: IptvRepository) : ViewModel() {

  private val panel = MutableStateFlow<PanelState>(PanelState.CategoriesMenu)
  private val currentChannelId = MutableStateFlow<String?>(null)
  private val searchQuery = MutableStateFlow("")
  private val refreshing = MutableStateFlow(false)
  private val refreshMessage = MutableStateFlow<String?>(null)
  private var startedInitialSelection = false

  private val listChannels =
    panel.flatMapLatest { state ->
      val source = (state as? PanelState.ChannelList)?.source ?: return@flatMapLatest flowOf(emptyList())
      when (source) {
        ChannelListSource.Favourites -> repository.bookmarkedChannels
        ChannelListSource.AllChannels -> repository.allChannels
        is ChannelListSource.Category -> repository.channelsByCategory(source.id)
        is ChannelListSource.Country -> repository.channelsByCountry(source.code)
        ChannelListSource.Search ->
          searchQuery.flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.search(q) }
      }
    }

  private val currentChannel = currentChannelId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.channelById(id) }

  private val currentStreamUrls =
    currentChannel.flatMapLatest { channel ->
      if (channel == null) flowOf(emptyList())
      else repository.streamUrls(channel.id).map { urls -> orderedByPreference(urls.map { it.url }, channel.selectedSourceUrl) }
    }

  private val bookmarkedIds = repository.bookmarkedChannels.map { list -> list.map { it.id }.toSet() }

  private data class BrowseState(
    val panel: PanelState,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
    val listChannels: List<ChannelEntity>,
    val bookmarkedIds: Set<String>,
  )

  private data class PlayerState(val currentChannel: ChannelEntity?, val currentStreamUrls: List<String>)

  private val browseState =
    combine(panel, repository.categories, repository.countries, listChannels, bookmarkedIds) { p, cats, countries, chans, bm ->
      BrowseState(p, cats, countries, chans, bm)
    }

  private val playerState = combine(currentChannel, currentStreamUrls, ::PlayerState)

  val uiState =
    combine(browseState, playerState, searchQuery, refreshing, refreshMessage) { browse, player, query, isRefreshing, message ->
        TvHomeUiState(
          panel = browse.panel,
          categories = browse.categories,
          countries = browse.countries,
          listChannels = browse.listChannels,
          bookmarkedIds = browse.bookmarkedIds,
          currentChannel = player.currentChannel,
          currentStreamUrls = player.currentStreamUrls,
          searchQuery = query,
          refreshing = isRefreshing,
          refreshMessage = message,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TvHomeUiState())

  init {
    viewModelScope.launch {
      if (startedInitialSelection) return@launch
      startedInitialSelection = true
      val bookmarks = repository.bookmarkedChannels.first()
      if (bookmarks.isNotEmpty()) {
        panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
        currentChannelId.value = bookmarks.first().id
      } else {
        panel.value = PanelState.ChannelList(ChannelListSource.AllChannels)
        currentChannelId.value = repository.allChannels.first().firstOrNull()?.id
      }
    }
  }

  fun onSelectChannel(channelId: String) {
    currentChannelId.value = channelId
  }

  fun onSelectCategoryRow(category: CategoryEntity) {
    panel.value = PanelState.ChannelList(ChannelListSource.Category(category.id, category.name))
  }

  fun onSelectAllChannelsRow() {
    panel.value = PanelState.ChannelList(ChannelListSource.AllChannels)
  }

  fun onSelectCountriesRow() {
    panel.value = PanelState.CountriesMenu
  }

  fun onSelectCountryRow(country: CountryEntity) {
    panel.value = PanelState.ChannelList(ChannelListSource.Country(country.code, country.name))
  }

  fun onPinnedFavourites() {
    panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
  }

  fun onPinnedSearch() {
    searchQuery.value = ""
    panel.value = PanelState.ChannelList(ChannelListSource.Search)
  }

  fun onPinnedCategories() {
    panel.value = PanelState.CategoriesMenu
  }

  fun onSearchQueryChange(query: String) {
    searchQuery.value = query
  }

  fun onBack(): Boolean {
    val current = panel.value
    if (current == PanelState.CategoriesMenu) return false // let the system handle it (exit)
    panel.value = current.backTarget()
    return true
  }

  fun onToggleBookmark(channel: ChannelEntity) {
    viewModelScope.launch {
      if (channel.id in bookmarkedIds.first()) repository.removeBookmark(channel.id) else repository.addBookmark(channel.id)
    }
  }

  fun onRefresh() {
    if (refreshing.value) return
    viewModelScope.launch {
      refreshing.value = true
      refreshMessage.value = null
      refreshMessage.value =
        runCatching { repository.refresh() }
          .fold(
            onSuccess = { r -> "${r.added} added, ${r.removed} removed" + if (r.bookmarksRemoved > 0) ", ${r.bookmarksRemoved} bookmarks removed" else "" },
            onFailure = { e -> "Refresh failed: ${e.message}" },
          )
      refreshing.value = false
    }
  }
}

/** Moves the manually-selected source (if any) to the front; automatic fallback then walks this list in order. */
private fun orderedByPreference(urls: List<String>, preferred: String?): List<String> =
  if (preferred == null || preferred !in urls) urls else listOf(preferred) + urls.filterNot { it == preferred }
