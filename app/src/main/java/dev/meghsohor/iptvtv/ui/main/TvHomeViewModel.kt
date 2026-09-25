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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TvHomeUiState(
  val panel: PanelState = PanelState.CategoriesMenu,
  /** False until launch has picked the opening view (Favourites or Categories) — [panel] may still change before that. */
  val startupPanelChosen: Boolean = false,
  val categories: List<CategoryEntity> = emptyList(),
  val countries: List<CountryEntity> = emptyList(),
  /** Null while the current [panel]'s list is still loading. */
  val listChannels: List<ChannelEntity>? = null,
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

  /** Ordered channel ids of the list [currentChannelId] was selected from — see "Channel change (zap)" in the spec. */
  private val currentPlaybackList = MutableStateFlow<List<String>>(emptyList())
  private val searchQuery = MutableStateFlow("")
  private val refreshing = MutableStateFlow(false)
  private val refreshMessage = MutableStateFlow<String?>(null)
  private var startedInitialSelection = false

  /** A loaded list, tagged with the panel it was loaded for. */
  private data class LoadedList(val panel: PanelState, val channels: List<ChannelEntity>)

  private val listChannels =
    panel.flatMapLatest { state ->
      val source = (state as? PanelState.ChannelList)?.source ?: return@flatMapLatest flowOf(LoadedList(state, emptyList()))
      when (source) {
        ChannelListSource.Favourites -> repository.bookmarkedChannels
        ChannelListSource.AllChannels -> repository.allChannels
        is ChannelListSource.Category -> repository.channelsByCategory(source.id)
        is ChannelListSource.Country -> repository.channelsByCountry(source.code)
        // Debounced: each keystroke would otherwise run a LIKE scan over ~11k rows, which a low-end TV feels.
        ChannelListSource.Search ->
          searchQuery.debounce(SearchDebounceMs).flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.search(q) }
      }.map { LoadedList(state, it) }
    }

  private val bookmarkedIds = repository.bookmarkedChannels.map { list -> list.map { it.id }.toSet() }

  private data class BrowseState(
    val panel: PanelState,
    val startupPanelChosen: Boolean,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
    val listChannels: List<ChannelEntity>?,
    val bookmarkedIds: Set<String>,
  )

  private data class PlayerState(val currentChannel: ChannelEntity?, val currentStreamUrls: List<String>)

  private val startupPanelChosen = MutableStateFlow(false)

  private val browseState =
    combine(combine(panel, startupPanelChosen, ::Pair), repository.categories, repository.countries, listChannels, bookmarkedIds) {
      (p, chosen), cats, countries, loaded, bm ->
      // Right after a panel change, the previous panel's list is still the latest one loaded —
      // report "loading" rather than show it (and its count) under the new heading.
      BrowseState(p, chosen, cats, countries, loaded.channels.takeIf { loaded.panel == p }, bm)
    }

  private val playerState =
    currentChannelId
      .flatMapLatest { id ->
        if (id == null) {
          flowOf(PlayerState(null, emptyList()))
        } else {
          combine(repository.channelById(id), repository.streamUrls(id)) { channel, urls ->
            if (channel == null) PlayerState(null, emptyList())
            else PlayerState(channel, orderedByPreference(urls.map { it.url }, channel.selectedSourceUrl))
          }
        }
      }
      // A refresh can cascade-delete the row for whatever's currently playing (or channel zap can
      // land on an id a refresh just removed) — keep the last known-good channel/URLs instead of
      // dropping to the "nothing playing" banner mid-watch; refresh only updates the dataset.
      .runningReduce { previous, new -> if (new.currentChannel == null) previous else new }
      // Shared for the ViewModel's lifetime: uiState stops collecting 5 s into the background, and a
      // restarted chain would have no "last good" state to fall back on, tearing the player down.
      .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState(null, emptyList()))

  val uiState =
    combine(browseState, playerState, searchQuery, refreshing, refreshMessage) { browse, player, query, isRefreshing, message ->
        TvHomeUiState(
          panel = browse.panel,
          startupPanelChosen = browse.startupPanelChosen,
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
      // No autoplay: open on Favourites if there are any (Categories otherwise) and wait for a pick.
      if (repository.bookmarkedChannels.first().isNotEmpty()) panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
      startupPanelChosen.value = true
      // Nothing locally yet (first launch) — fetch before the user has to think to hit Refresh.
      if (!repository.hasChannels()) onRefresh() else repository.pruneEmptyMenus()
    }
  }

  fun onSelectChannel(channelId: String) {
    currentChannelId.value = channelId
    currentPlaybackList.value = uiState.value.listChannels.orEmpty().map { it.id }
  }

  /** Steps to the next/previous channel within [currentPlaybackList], wrapping at either end. Doesn't touch panel/nav state. */
  fun onChannelUp() = stepChannel(1)

  fun onChannelDown() = stepChannel(-1)

  private fun stepChannel(delta: Int) {
    val list = currentPlaybackList.value
    val index = list.indexOf(currentChannelId.value)
    if (index == -1) return
    currentChannelId.value = list[(index + delta).mod(list.size)]
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

  /** Up one level; a no-op at the top (Categories), where the screen exits instead. */
  fun onBack() {
    panel.value = panel.value.backTarget()
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
      val result = runCatching { repository.refresh() }
      refreshMessage.value =
        result.fold(
          onSuccess = { r -> "${r.added} added, ${r.removed} removed" + if (r.bookmarksRemoved > 0) ", ${r.bookmarksRemoved} bookmarks removed" else "" },
          onFailure = { e -> "Refresh failed: ${e.message}" },
        )
      if (result.isSuccess) {
        // A refresh can remove channels; drop any that were in the zap list so stepping through
        // it can't land on an id that no longer exists. The playing one stays even if removed (it
        // keeps playing), or Channel Up/Down would have nothing to step from.
        val validIds = repository.allChannelIds().toSet()
        val playing = currentChannelId.value
        currentPlaybackList.value = currentPlaybackList.value.filter { it in validIds || it == playing }
      }
      refreshing.value = false
    }
  }

  fun onDismissRefreshMessage() {
    refreshMessage.value = null
  }
}

private const val SearchDebounceMs = 250L

/** Moves the manually-selected source (if any) to the front; automatic fallback then walks this list in order. */
private fun orderedByPreference(urls: List<String>, preferred: String?): List<String> =
  if (preferred == null || preferred !in urls) urls else listOf(preferred) + urls.filterNot { it == preferred }
