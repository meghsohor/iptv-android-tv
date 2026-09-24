package dev.meghsohor.iptvtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.meghsohor.iptvtv.R
import dev.meghsohor.iptvtv.data.IptvRepository
import dev.meghsohor.iptvtv.data.db.CategoryEntity
import dev.meghsohor.iptvtv.data.db.ChannelEntity
import dev.meghsohor.iptvtv.data.db.CountryEntity
import dev.meghsohor.iptvtv.theme.MeghLive
import dev.meghsohor.iptvtv.ui.player.VideoPlayer
import kotlinx.coroutines.delay

private val PanelWidth = 340.dp
private const val PanelAutoHideDelayMs = 5000L

@Composable
fun TvHomeScreen(repository: IptvRepository, modifier: Modifier = Modifier) {
  val viewModel: TvHomeViewModel = viewModel { TvHomeViewModel(repository) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler(enabled = state.panel != PanelState.CategoriesMenu) { viewModel.onBack() }

  // Panel auto-hide: any nav key (or a touch tap on the video, for phones) bumps activityTick
  // (restarting the hide countdown) and reveals the panel; Channel Up/Down and volume
  // deliberately don't touch either, per the spec.
  var panelVisible by remember { mutableStateOf(true) }
  var activityTick by remember { mutableIntStateOf(0) }
  val rootFocusRequester = remember { FocusRequester() }
  val panelEntryFocusRequester = remember { FocusRequester() }

  fun registerActivity() {
    panelVisible = true
    activityTick++
  }

  // The refresh dialog blocks interaction outright, so the panel has no business disappearing
  // underneath it — stay visible for the whole refreshing -> result-shown lifecycle, and only
  // start the countdown once the user actually dismisses the result. Same idea while the Search
  // field is focused: a hide mid-typing would yank the keyboard focus out from under the user.
  var searchFieldFocused by remember { mutableStateOf(false) }
  val suppressAutoHide = state.refreshing || state.refreshMessage != null || searchFieldFocused
  LaunchedEffect(suppressAutoHide) { if (suppressAutoHide) panelVisible = true }

  LaunchedEffect(activityTick, suppressAutoHide) {
    if (suppressAutoHide) return@LaunchedEffect
    delay(PanelAutoHideDelayMs)
    panelVisible = false
  }

  // Keyed on the flip itself (not activityTick) so it doesn't steal focus mid-browse.
  LaunchedEffect(panelVisible) {
    if (panelVisible) panelEntryFocusRequester.requestFocus() else rootFocusRequester.requestFocus()
  }

  Box(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .focusRequester(rootFocusRequester)
      .focusable()
      .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { registerActivity() }
      .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.ChannelUp -> {
            viewModel.onChannelUp()
            true
          }
          Key.ChannelDown -> {
            viewModel.onChannelDown()
            true
          }
          Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.Back -> {
            registerActivity()
            false // don't consume — let normal focus/back handling still run
          }
          else -> false
        }
      }
  ) {
    if (state.currentStreamUrls.isNotEmpty()) {
      VideoPlayer(streamUrls = state.currentStreamUrls, modifier = Modifier.fillMaxSize(), onInteraction = ::registerActivity)
      NowPlayingBar(channel = state.currentChannel, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
    } else {
      Image(
        painter = painterResource(R.drawable.tv_banner),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }

    if (panelVisible) {
      SidePanel(
        state = state,
        viewModel = viewModel,
        entryFocusRequester = panelEntryFocusRequester,
        onSearchFieldFocusChanged = { searchFieldFocused = it },
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(PanelWidth),
      )
    }

    if (state.refreshing || state.refreshMessage != null) {
      RefreshDialog(refreshing = state.refreshing, message = state.refreshMessage, onDismiss = viewModel::onDismissRefreshMessage)
    }
  }
}

@Composable
private fun NowPlayingBar(channel: ChannelEntity?, modifier: Modifier = Modifier) {
  Row(
    modifier.background(Color(0xCC070B1A)).padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      Modifier.clip(RoundedCornerShape(4.dp)).background(MeghLive.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(MeghLive))
      Text("LIVE", color = MeghLive, style = MaterialTheme.typography.labelLarge)
    }
    Text(
      channel?.displayName ?: "Select a channel",
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.labelLarge,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SidePanel(
  state: TvHomeUiState,
  viewModel: TvHomeViewModel,
  entryFocusRequester: FocusRequester,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
    PinnedRow(label = "Refresh Channels", onClick = viewModel::onRefresh, modifier = Modifier.focusRequester(entryFocusRequester))
    PinnedRow(label = "Search", selected = isSearch(state.panel), onClick = viewModel::onPinnedSearch)
    PinnedRow(label = "Favourites", selected = isFavourites(state.panel), onClick = viewModel::onPinnedFavourites)
    PinnedRow(label = "Categories", selected = state.panel == PanelState.CategoriesMenu, onClick = viewModel::onPinnedCategories)

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
            currentChannelId = state.currentChannel?.id,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchFieldFocusChanged = onSearchFieldFocusChanged,
            onSelectChannel = viewModel::onSelectChannel,
            onToggleBookmark = viewModel::onToggleBookmark,
          )
      }
    }
  }
}

private fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

private fun isFavourites(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Favourites

/** Shared focus/selection treatment for every row style in the panel — animated tint + a solid accent bar on the focused row. */
@Composable
private fun Modifier.rowStyle(focused: Boolean, selected: Boolean = false): Modifier {
  val background by
    animateColorAsState(
      when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> Color.Transparent
      },
      label = "rowBackground",
    )
  val accent = MaterialTheme.colorScheme.primary
  return this.background(background).drawBehind { if (focused) drawRect(color = accent, size = size.copy(width = 4.dp.toPx())) }
}

@Composable
private fun PinnedRow(label: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    modifier
      .fillMaxWidth()
      .rowStyle(focused, selected)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(20.dp, 16.dp),
  ) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
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
        PlainRow("${country.flag}  ${country.name}", onClick = { viewModel.onSelectCountryRow(country) })
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
  currentChannelId: String?,
  onSearchQueryChange: (String) -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onSelectChannel: (String) -> Unit,
  onToggleBookmark: (ChannelEntity) -> Unit,
) {
  val flagByCountry = remember(countries) { countries.associate { it.code to it.flag } }

  Column {
    HeaderRow(headerName)
    // Only Search gets a text field — everywhere else, Search itself is the one place to filter
    // by name, so a second per-list filter box was redundant (and the reason lists used to get a
    // D-pad focus stuck at the top instead of scrolling).
    if (isSearch) {
      FilterField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = "Search channels…",
        onFocusChanged = onSearchFieldFocusChanged,
      )
    }
    LazyColumn {
      items(channels, key = { it.id }) { channel ->
        ChannelRow(
          channel = channel,
          flag = flagByCountry[channel.countryCode].orEmpty(),
          bookmarked = channel.id in bookmarkedIds,
          playing = channel.id == currentChannelId,
          onClick = { onSelectChannel(channel.id) },
          onToggleBookmark = { onToggleBookmark(channel) },
        )
      }
    }
  }
}

@Composable
private fun HeaderRow(title: String) {
  Text(
    title,
    color = MaterialTheme.colorScheme.onSurface,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp),
  )
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, placeholder: String, onFocusChanged: (Boolean) -> Unit = {}) {
  // A text field otherwise swallows DPAD_DOWN as a (no-op) cursor-move key instead of letting
  // focus continue into the list below — intercept it here, before the field sees it, and hand
  // it to normal focus traversal instead. Standard gotcha wiring a plain text field into a D-pad UI.
  val focusManager = LocalFocusManager.current
  Box(
    Modifier.fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(12.dp, 10.dp)
      .onFocusChanged { onFocusChanged(it.hasFocus) } // Box itself isn't focusable — BasicTextField below is
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
          focusManager.moveFocus(FocusDirection.Down)
          true
        } else {
          false
        }
      }
  ) {
    if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    )
  }
}

@Composable
private fun PlainRow(label: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    Modifier.fillMaxWidth()
      .rowStyle(focused)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(20.dp, 14.dp),
  ) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun ChannelRow(
  channel: ChannelEntity,
  flag: String,
  bookmarked: Boolean,
  playing: Boolean,
  onClick: () -> Unit,
  onToggleBookmark: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Row(
    Modifier.fillMaxWidth()
      .rowStyle(focused, selected = playing)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(20.dp, 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (playing) Text("▶  ", color = Color.White, style = MaterialTheme.typography.bodyMedium)
      Text(
        "$flag  ${channel.displayName}",
        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = Modifier.weight(1f, fill = false),
      )
    }
    val starInteraction = remember { MutableInteractionSource() }
    val starFocused by starInteraction.collectIsFocusedAsState()
    Text(
      if (bookmarked) "★" else "☆",
      color = if (bookmarked) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
        Modifier.clip(RoundedCornerShape(6.dp))
          .background(if (starFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
          .clickable(interactionSource = starInteraction, indication = null, onClick = onToggleBookmark)
          .focusable(interactionSource = starInteraction)
          .padding(6.dp),
    )
  }
}

private const val RefreshResultAutoCloseMs = 4000L

@Composable
private fun RefreshDialog(refreshing: Boolean, message: String?, onDismiss: () -> Unit) {
  LaunchedEffect(refreshing, message) {
    if (!refreshing && message != null) {
      delay(RefreshResultAutoCloseMs)
      onDismiss()
    }
  }

  Dialog(
    onDismissRequest = { if (!refreshing) onDismiss() },
    properties = DialogProperties(dismissOnBackPress = !refreshing, dismissOnClickOutside = false),
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
      Column(
        Modifier.widthIn(min = 280.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        if (refreshing) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          Text("Refreshing channels…", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
        } else if (message != null) {
          Text(message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
          Box(Modifier.fillMaxWidth()) { CloseButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) }
        }
      }
    }
  }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Text(
    "Close",
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleSmall,
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
        .padding(horizontal = 16.dp, vertical = 8.dp),
  )
}
