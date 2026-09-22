package dev.meghsohor.iptvtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.meghsohor.iptvtv.data.IptvRepository
import dev.meghsohor.iptvtv.data.db.CategoryEntity
import dev.meghsohor.iptvtv.data.db.ChannelEntity
import dev.meghsohor.iptvtv.data.db.CountryEntity
import dev.meghsohor.iptvtv.ui.player.VideoPlayer

private val PanelWidth = 340.dp
private val PanelBackground = Color(0xCC121317)

@Composable
fun TvHomeScreen(repository: IptvRepository, modifier: Modifier = Modifier) {
  val viewModel: TvHomeViewModel = viewModel { TvHomeViewModel(repository) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler(enabled = state.panel != PanelState.CategoriesMenu) { viewModel.onBack() }

  Box(modifier.fillMaxSize().background(Color.Black)) {
    if (state.currentStreamUrls.isNotEmpty()) {
      VideoPlayer(streamUrls = state.currentStreamUrls, modifier = Modifier.fillMaxSize())
    }

    NowPlayingBar(channel = state.currentChannel, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())

    SidePanel(state = state, viewModel = viewModel, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(PanelWidth))
  }
}

@Composable
private fun NowPlayingBar(channel: ChannelEntity?, modifier: Modifier = Modifier) {
  Row(modifier.background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("● LIVE", color = Color(0xFFE53935), style = MaterialTheme.typography.labelLarge)
    Text(
      "Now Playing: ${channel?.displayName ?: "—"}",
      color = Color.White,
      style = MaterialTheme.typography.labelLarge,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SidePanel(state: TvHomeUiState, viewModel: TvHomeViewModel, modifier: Modifier = Modifier) {
  Column(modifier.background(PanelBackground)) {
    PinnedRow(label = if (state.refreshing) "Refreshing…" else "Refresh Channels", onClick = viewModel::onRefresh)
    PinnedRow(label = "Search", selected = isSearch(state.panel), onClick = viewModel::onPinnedSearch)
    PinnedRow(label = "Favourites", selected = isFavourites(state.panel), onClick = viewModel::onPinnedFavourites)
    PinnedRow(label = "Categories", selected = state.panel == PanelState.CategoriesMenu, onClick = viewModel::onPinnedCategories)

    state.refreshMessage?.let { msg -> Text(msg, color = Color(0xFFB0B0B0), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp, 4.dp)) }

    Box(Modifier.weight(1f)) {
      when (val panel = state.panel) {
        PanelState.CategoriesMenu -> CategoriesMenuContent(categories = state.categories, viewModel = viewModel)
        PanelState.CountriesMenu -> CountriesMenuContent(countries = state.countries, viewModel = viewModel)
        is PanelState.ChannelList ->
          ChannelListContent(
            headerName = panel.source.headerName,
            isSearch = panel.source == ChannelListSource.Search,
            searchQuery = state.searchQuery,
            channels = state.listChannels,
            countries = state.countries,
            bookmarkedIds = state.bookmarkedIds,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSelectChannel = viewModel::onSelectChannel,
            onToggleBookmark = viewModel::onToggleBookmark,
          )
      }
    }
  }
}

private fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

private fun isFavourites(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Favourites

@Composable
private fun PinnedRow(label: String, onClick: () -> Unit, selected: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    Modifier.fillMaxWidth()
      .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
      .border(if (focused) 2.dp else 0.dp, Color(0xFF4FC3F7))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(16.dp, 14.dp),
  ) {
    Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
private fun CategoriesMenuContent(categories: List<CategoryEntity>, viewModel: TvHomeViewModel) {
  LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
    item { PlainRow("All Channels", onClick = viewModel::onSelectAllChannelsRow) }
    item { PlainRow("Countries", onClick = viewModel::onSelectCountriesRow) }
    items(categories, key = { it.id }) { category -> PlainRow(category.name, onClick = { viewModel.onSelectCategoryRow(category) }) }
  }
}

@Composable
private fun CountriesMenuContent(countries: List<CountryEntity>, viewModel: TvHomeViewModel) {
  Column {
    HeaderRow("Countries")
    LazyColumn {
      items(countries, key = { it.code }) { country ->
        PlainRow("${country.flag} ${country.name}", onClick = { viewModel.onSelectCountryRow(country) })
      }
    }
  }
}

@Composable
private fun ChannelListContent(
  headerName: String,
  isSearch: Boolean,
  searchQuery: String,
  channels: List<ChannelEntity>,
  countries: List<CountryEntity>,
  bookmarkedIds: Set<String>,
  onSearchQueryChange: (String) -> Unit,
  onSelectChannel: (String) -> Unit,
  onToggleBookmark: (ChannelEntity) -> Unit,
) {
  var localFilter by remember(headerName) { androidx.compose.runtime.mutableStateOf("") }
  val flagByCountry = remember(countries) { countries.associate { it.code to it.flag } }
  val visible = remember(channels, localFilter) { channels.filter { it.displayName.contains(localFilter, ignoreCase = true) } }

  Column {
    HeaderRow(headerName)
    if (isSearch) {
      FilterField(value = searchQuery, onValueChange = onSearchQueryChange, placeholder = "Search channels…")
    } else {
      FilterField(value = localFilter, onValueChange = { localFilter = it }, placeholder = "Filter…")
    }
    LazyColumn {
      items(if (isSearch) channels else visible, key = { it.id }) { channel ->
        ChannelRow(
          channel = channel,
          flag = flagByCountry[channel.countryCode].orEmpty(),
          bookmarked = channel.id in bookmarkedIds,
          onClick = { onSelectChannel(channel.id) },
          onToggleBookmark = { onToggleBookmark(channel) },
        )
      }
    }
  }
}

@Composable
private fun HeaderRow(title: String) {
  Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp))
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
  // A text field otherwise swallows DPAD_DOWN as a (no-op) cursor-move key instead of letting
  // focus continue into the list below — intercept it here, before the field sees it, and hand
  // it to normal focus traversal instead. Standard gotcha wiring a plain text field into a D-pad UI.
  val focusManager = LocalFocusManager.current
  Box(
    Modifier.fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .background(Color(0x1AFFFFFF), RoundedCornerShape(6.dp))
      .padding(10.dp, 8.dp)
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
          focusManager.moveFocus(FocusDirection.Down)
          true
        } else {
          false
        }
      }
  ) {
    if (value.isEmpty()) Text(placeholder, color = Color(0xFF9E9E9E), style = MaterialTheme.typography.bodyMedium)
    BasicTextField(value = value, onValueChange = onValueChange, textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
  }
}

@Composable
private fun PlainRow(label: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    Modifier.fillMaxWidth()
      .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(16.dp, 12.dp),
  ) {
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun ChannelRow(channel: ChannelEntity, flag: String, bookmarked: Boolean, onClick: () -> Unit, onToggleBookmark: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    Modifier.fillMaxWidth()
      .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(16.dp, 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text("$flag ${channel.displayName}", color = Color.White, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(
      if (bookmarked) "★" else "☆",
      color = if (bookmarked) Color(0xFFFFC107) else Color(0xFF9E9E9E),
      modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleBookmark),
    )
  }
}
