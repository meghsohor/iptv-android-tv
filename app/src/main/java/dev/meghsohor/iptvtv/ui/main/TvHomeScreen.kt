package dev.meghsohor.iptvtv.ui.main

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import dev.meghsohor.iptvtv.theme.MeghBackground
import dev.meghsohor.iptvtv.theme.MeghLive
import dev.meghsohor.iptvtv.ui.MeghIcons
import dev.meghsohor.iptvtv.ui.player.PlayerCommand
import dev.meghsohor.iptvtv.ui.player.VideoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val PanelWidth = 340.dp
private const val PanelAutoHideDelayMs = 5000L
private const val SameBackPressWindowMs = 200L
private val PanelNavigationKeys =
  setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Below this, four stacked pinned rows would eat half the panel (a phone in landscape) — they collapse into one tab row. */
private val CompactPanelHeight = 480.dp

@Composable
fun TvHomeScreen(repository: IptvRepository, modifier: Modifier = Modifier) {
  val viewModel: TvHomeViewModel = viewModel { TvHomeViewModel(repository) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val touchMode = LocalInputModeManager.current.inputMode == InputMode.Touch

  // Panel auto-hide (TV only): any arrow key bumps activityTick (restarting the hide countdown) and
  // reveals the panel; Channel Up/Down and volume deliberately don't touch either, per the spec. On
  // touch the panel never times out — it opens from its edge handle and closes on a tap outside it.
  var panelOpen by remember { mutableStateOf(true) }
  var activityTick by remember { mutableIntStateOf(0) }
  var searchFieldFocused by remember { mutableStateOf(false) }
  var playerControlsVisible by remember { mutableStateOf(false) }
  val rootFocusRequester = remember { FocusRequester() }
  val panelEntryFocusRequester = remember { FocusRequester() }
  // One scroll position per panel view, hoisted above the panel itself: closing the panel (or
  // stepping back from a country's channels) returns to exactly where the list was left.
  val listStates = remember { mutableMapOf<PanelState, LazyListState>() }
  val listState = listStates.getOrPut(state.panel) { LazyListState() }
  // Which row each menu was last left through, so D-pad focus can land back on it.
  val openedFrom = remember { mutableMapOf<PanelState, Int>() }
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val playerCommands = remember { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 1) }
  val activity = LocalActivity.current
  var rootHasFocus by remember { mutableStateOf(false) }
  var rootSelfFocused by remember { mutableStateOf(false) }
  val hasPlayer = state.currentStreamUrls.isNotEmpty() && state.currentChannel != null

  fun openPanel() {
    panelOpen = true
    activityTick++
  }

  // Back peels off one layer at a time: player controls, then brings a hidden panel back, and only
  // once the panel is visible does it walk up a level. On Android 13+ one press can land here twice
  // (the key event forwarded below, then the platform's own back callback) — the echo is ignored,
  // or a single press would both reveal the panel and navigate up.
  val lastBackAt = remember { longArrayOf(0L) }
  BackHandler {
    val now = SystemClock.uptimeMillis()
    if (now - lastBackAt[0] < SameBackPressWindowMs) return@BackHandler
    lastBackAt[0] = now
    when {
      !panelOpen && playerControlsVisible -> playerCommands.tryEmit(PlayerCommand.HideControls)
      !panelOpen -> openPanel()
      state.panel != PanelState.CategoriesMenu -> {
        viewModel.onBack()
        openPanel()
      }
      // A real exit. Left to the system, Android 12+ only moves the task to the back, and reopening
      // from the launcher would resume the last channel — the app would look like it autoplays.
      else -> activity?.finish()
    }
  }

  // The refresh dialog blocks interaction outright, so the panel has no business disappearing
  // underneath it — stay visible for the whole refreshing -> result-shown lifecycle. Same while
  // typing a search: a hide there would yank the keyboard out from under the user.
  val refreshInProgress = state.refreshing || state.refreshMessage != null
  val suppressAutoHide = touchMode || refreshInProgress || searchFieldFocused
  LaunchedEffect(refreshInProgress) { if (refreshInProgress) panelOpen = true }

  LaunchedEffect(activityTick, suppressAutoHide) {
    if (suppressAutoHide) return@LaunchedEffect
    delay(PanelAutoHideDelayMs)
    panelOpen = false
  }

  // Keyed on the flip itself (not activityTick) so it doesn't steal focus mid-browse. No entry
  // focus on touch: it would only paint a D-pad highlight on a row nobody selected. At launch it
  // waits for the opening view, or focus lands on Categories just before Favourites replaces it.
  LaunchedEffect(panelOpen, state.startupPanelChosen) {
    if (!panelOpen) rootFocusRequester.requestFocus()
    else if (!touchMode && state.startupPanelChosen) panelEntryFocusRequester.requestFocus()
  }

  // Removing the focused element (Retry once a channel change clears the error, a row that was just
  // opened or un-starred) drops focus for the whole screen — and with it every remote key, since keys
  // only arrive here while something inside the root is focused. Take it back; the next arrow or OK
  // then moves it on into the panel (below).
  LaunchedEffect(rootHasFocus) {
    if (rootHasFocus) return@LaunchedEffect
    withFrameNanos {}
    withFrameNanos {}
    if (!rootHasFocus) runCatching { rootFocusRequester.requestFocus() }
  }

  Box(
    modifier
      .fillMaxSize()
      // No background of its own: the window's (same navy) is already there, and every extra
      // full-screen fill is real cost on a low-end TV GPU.
      // Keeps everything clear of the navigation bar and camera cutout; the video is letterboxed
      // on a phone anyway, so this costs no picture.
      .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      .focusRequester(rootFocusRequester)
      .onFocusChanged {
        rootHasFocus = it.hasFocus
        rootSelfFocused = it.isFocused
      }
      .focusable()
      .onPreviewKeyEvent { event ->
        // Left alone, Compose turns Back into "move focus out of the focused row" and consumes it,
        // so with a row focused the first press did nothing visible. Send it straight to the back
        // dispatcher instead (on key-up, as the system does), which runs the BackHandler above.
        if (event.key == Key.Back && backDispatcher != null) {
          if (event.type == KeyEventType.KeyUp) backDispatcher.onBackPressed()
          return@onPreviewKeyEvent true
        }
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // A held key repeats KeyDown; toggles act on the first one only, repeats are swallowed.
        val repeated = event.nativeKeyEvent.repeatCount > 0
        // Focus parked on the root itself while the panel is up (see the focus-loss catch above):
        // directional search never looks inside the focused node, so hand focus to the panel directly.
        if (panelOpen && rootSelfFocused && !touchMode && event.key in PanelNavigationKeys) {
          openPanel()
          runCatching { panelEntryFocusRequester.requestFocus() }
          return@onPreviewKeyEvent true
        }
        when (event.key) {
          Key.ChannelUp -> {
            viewModel.onChannelUp()
            true
          }
          Key.ChannelDown -> {
            viewModel.onChannelDown()
            true
          }
          Key.MediaPlayPause -> repeated || playerCommands.tryEmit(PlayerCommand.TogglePlayPause)
          Key.MediaPlay -> playerCommands.tryEmit(PlayerCommand.Play)
          Key.MediaPause -> playerCommands.tryEmit(PlayerCommand.Pause)
          // With the panel away, OK works like a tap on a phone: shows the controls, then plays/pauses
          // — the only way to pause on remotes without media keys (e.g. Chromecast with Google TV).
          Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
            when {
              panelOpen -> {
                openPanel() // keeps the auto-hide timer alive; the focused row handles the click
                false
              }
              !rootSelfFocused -> false // something on the video has focus (Retry): let it act
              playerControlsVisible -> repeated || playerCommands.tryEmit(PlayerCommand.TogglePlayPause)
              hasPlayer -> repeated || playerCommands.tryEmit(PlayerCommand.ShowControls)
              else -> {
                openPanel()
                false
              }
            }
          Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
            openPanel()
            false // don't consume — let normal focus handling still run
          }
          else -> false
        }
      }
  ) {
    val currentChannel = state.currentChannel
    if (hasPlayer && currentChannel != null) {
      VideoPlayer(
        channelId = currentChannel.id,
        streamUrls = state.currentStreamUrls,
        controlsAllowed = !panelOpen,
        touchControls = touchMode,
        onTap = {
          if (panelOpen) {
            panelOpen = false
            true
          } else {
            false
          }
        },
        onControlsVisibilityChange = { playerControlsVisible = it },
        overlayEndPadding = if (panelOpen) PanelWidth else 0.dp,
        playerCommands = playerCommands,
        modifier = Modifier.fillMaxSize(),
      )
      if (panelOpen || playerControlsVisible) {
        NowPlayingBadge(channel = currentChannel, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
      }
    } else {
      Image(
        painter = painterResource(R.drawable.tv_banner),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Tappable only while there's a menu to close — otherwise a screen reader would announce a no-op action.
        modifier =
          Modifier.fillMaxSize()
            .then(
              if (panelOpen) {
                Modifier.clickable(onClickLabel = "Close menu", indication = null, interactionSource = null) { panelOpen = false }
                  .semantics { contentDescription = "Close menu" }
              } else {
                Modifier
              }
            ),
      )
    }

    if (panelOpen) {
      SidePanel(
        state = state,
        viewModel = viewModel,
        touchMode = touchMode,
        entryFocusRequester = panelEntryFocusRequester,
        onSearchFieldFocusChanged = { searchFieldFocused = it },
        listState = listState,
        openedFrom = openedFrom,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(PanelWidth),
      )
    } else if (touchMode) {
      PanelHandle(onClick = ::openPanel, modifier = Modifier.align(Alignment.CenterEnd))
    }

    if (refreshInProgress) {
      RefreshDialog(refreshing = state.refreshing, message = state.refreshMessage, onDismiss = viewModel::onDismissRefreshMessage)
    }
  }
}

@Composable
private fun NowPlayingBadge(channel: ChannelEntity, modifier: Modifier = Modifier) {
  Row(
    modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(8.dp)).background(MeghBackground.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
      channel.displayName,
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Edge tab the panel slides out from — the touch way back in once it's closed. */
@Composable
private fun PanelHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Box(
    modifier
      .size(width = 44.dp, height = 96.dp)
      // Translucent, but lighter than the navy letterbox bars it usually sits on — navy on navy vanishes.
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .semantics { contentDescription = "Open menu" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      MeghIcons.ChevronLeft,
      contentDescription = null,
      tint = if (pressed) Color.White else MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp),
    )
  }
}

@Composable
private fun SidePanel(
  state: TvHomeUiState,
  viewModel: TvHomeViewModel,
  touchMode: Boolean,
  entryFocusRequester: FocusRequester,
  listState: LazyListState,
  openedFrom: MutableMap<PanelState, Int>,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  // D-pad: opening a list (or going back) removes the row that had focus, and the next key press
  // would then restart at the top of the panel. Put focus on the row we came back through, or the
  // first visible one, instead. Skipped when focus survived the switch (a pinned row was pressed).
  var panelHasFocus by remember { mutableStateOf(false) }
  val contentFocusRequester = remember { FocusRequester() }
  val focusIndex = remember(state.panel) { openedFrom[state.panel] ?: listState.firstVisibleItemIndex }
  // A channel list has no rows until its query returns (listChannels == null); waiting for that
  // matters, since a revisited list's reused scroll state still describes the previous visit's rows.
  val contentReady = state.panel !is PanelState.ChannelList || state.listChannels != null
  // The panel it opened on is focused by the caller — skipped here until the panel first changes.
  val openedOn = remember { arrayOf<PanelState?>(state.panel) }
  LaunchedEffect(state.panel, contentReady) {
    if (openedOn[0] != state.panel) openedOn[0] = null
    if (touchMode || !contentReady || openedOn[0] != null) return@LaunchedEffect
    withFrameNanos {} // compose the list...
    withFrameNanos {} // ...and lay it out
    if (panelHasFocus) return@LaunchedEffect
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) listState.scrollToItem(focusIndex)
    val shown = withTimeoutOrNull(2_000) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == focusIndex } }.first { it } }
    if (shown != null) {
      withFrameNanos {}
      runCatching { contentFocusRequester.requestFocus() } // the row may have scrolled away meanwhile
    }
  }
  // The panel opens with focus on the highlighted tab, not on Refresh: OK pressed twice to "wake"
  // the panel would otherwise start a full network refresh.
  fun entry(selected: Boolean) = if (selected) Modifier.focusRequester(entryFocusRequester) else Modifier
  fun open(index: Int, navigate: () -> Unit) {
    openedFrom[state.panel] = index
    navigate()
  }

  BoxWithConstraints(
    modifier
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
      .onFocusChanged { panelHasFocus = it.hasFocus }
      // Registers as a touch target (observing, never consuming) so a tap on empty panel space
      // doesn't fall through to the video behind — which would close the panel.
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial) } }
      .imePadding()
  ) {
    val compact = maxHeight < CompactPanelHeight
    Column(Modifier.fillMaxSize()) {
      if (compact) {
        Row(Modifier.fillMaxWidth()) {
          PinnedRow("Refresh", viewModel::onRefresh, compact = true, modifier = Modifier.weight(1f))
          PinnedRow("Search", viewModel::onPinnedSearch, selected = isSearch(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isSearch(state.panel))))
          PinnedRow("Favourites", viewModel::onPinnedFavourites, selected = isFavourites(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isFavourites(state.panel))))
          PinnedRow("Categories", viewModel::onPinnedCategories, selected = isCategories(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isCategories(state.panel))))
        }
      } else {
        PinnedRow("Refresh Channels", viewModel::onRefresh, modifier = Modifier.fillMaxWidth())
        PinnedRow("Search", viewModel::onPinnedSearch, selected = isSearch(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isSearch(state.panel))))
        PinnedRow("Favourites", viewModel::onPinnedFavourites, selected = isFavourites(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isFavourites(state.panel))))
        PinnedRow("Categories", viewModel::onPinnedCategories, selected = isCategories(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isCategories(state.panel))))
      }

      // Nothing until launch has picked the opening view, or Categories flashes up before Favourites.
      Box(Modifier.weight(1f)) {
        if (state.startupPanelChosen) when (val panel = state.panel) {
          PanelState.CategoriesMenu ->
            CategoriesMenuContent(
              categories = state.categories,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              onAllChannels = { open(0) { viewModel.onSelectAllChannelsRow() } },
              onCountries = { open(1) { viewModel.onSelectCountriesRow() } },
              onCategory = { index, category -> open(index) { viewModel.onSelectCategoryRow(category) } },
            )
          PanelState.CountriesMenu ->
            CountriesMenuContent(
              countries = state.countries,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              onBack = { viewModel.onBack() },
              onCountry = { index, country -> open(index) { viewModel.onSelectCountryRow(country) } },
            )
          is PanelState.ChannelList ->
            ChannelListContent(
              source = panel.source,
              touchMode = touchMode,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              searchQuery = state.searchQuery,
              channels = state.listChannels,
              countries = state.countries,
              bookmarkedIds = state.bookmarkedIds,
              currentChannelId = state.currentChannel?.id,
              onBack = { viewModel.onBack() },
              onSearchQueryChange = viewModel::onSearchQueryChange,
              onSearchFieldFocusChanged = onSearchFieldFocusChanged,
              onSelectChannel = viewModel::onSelectChannel,
              onToggleBookmark = viewModel::onToggleBookmark,
            )
        }
      }
    }
  }
}

private fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

private fun isFavourites(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Favourites

/** Everything reached by drilling down from the Categories row, so that row stays highlighted there too. */
private fun isCategories(panel: PanelState) = !isSearch(panel) && !isFavourites(panel)

/**
 * Shared row treatment: the active row (selected tab, playing channel) is solid brand cyan — pair
 * it with [rowContentColor] — and a focused or pressed row gets a tint, plus a 4dp accent bar when
 * D-pad focused (white on the cyan row, where a cyan bar would vanish). No colour animation: that's
 * one more running animation per visible row, for nothing, on a low-end TV.
 */
@Composable
private fun Modifier.panelRow(interaction: MutableInteractionSource, selected: Boolean = false, onClick: () -> Unit): Modifier {
  val focused by interaction.collectIsFocusedAsState()
  val pressed by interaction.collectIsPressedAsState()
  val colors = MaterialTheme.colorScheme
  val background =
    when {
      selected -> colors.primary
      focused || pressed -> colors.surfaceVariant
      else -> Color.Transparent
    }
  val accent = if (selected) Color.White else colors.primary
  return this.background(background)
    .drawBehind { if (focused) drawRect(color = accent, size = size.copy(width = 4.dp.toPx())) }
    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    .focusable(interactionSource = interaction)
}

@Composable
private fun rowContentColor(selected: Boolean): Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

@Composable
private fun PinnedRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, compact: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier
      .panelRow(interaction, selected, onClick)
      .heightIn(min = if (compact) 48.dp else 52.dp)
      .padding(horizontal = if (compact) 4.dp else 20.dp),
    contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart,
  ) {
    Text(
      label,
      color = rowContentColor(selected),
      style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CategoriesMenuContent(
  categories: List<CategoryEntity>,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  onAllChannels: () -> Unit,
  onCountries: () -> Unit,
  onCategory: (index: Int, CategoryEntity) -> Unit,
) {
  fun rowModifier(index: Int) = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier
  LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
    item { PlainRow("All Channels", onClick = onAllChannels, modifier = rowModifier(0)) }
    item { PlainRow("Countries", onClick = onCountries, modifier = rowModifier(1)) }
    itemsIndexed(categories, key = { _, it -> it.id }) { i, category ->
      PlainRow(category.name, onClick = { onCategory(i + 2, category) }, modifier = rowModifier(i + 2))
    }
  }
}

@Composable
private fun CountriesMenuContent(
  countries: List<CountryEntity>,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  onBack: () -> Unit,
  onCountry: (index: Int, CountryEntity) -> Unit,
) {
  Column {
    BackHeader("Countries", onBack = onBack)
    LazyColumn(state = listState) {
      itemsIndexed(countries, key = { _, it -> it.code }) { i, country ->
        PlainRow(
          "${country.flag}  ${country.name}",
          onClick = { onCountry(i, country) },
          modifier = if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
        )
      }
    }
  }
}

@Composable
private fun ChannelListContent(
  source: ChannelListSource,
  touchMode: Boolean,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  searchQuery: String,
  channels: List<ChannelEntity>?,
  countries: List<CountryEntity>,
  bookmarkedIds: Set<String>,
  currentChannelId: String?,
  onBack: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onSelectChannel: (String) -> Unit,
  onToggleBookmark: (ChannelEntity) -> Unit,
) {
  val flagByCountry = remember(countries) { countries.associate { it.code to it.flag } }
  val isSearch = source == ChannelListSource.Search
  val keyboard = LocalSoftwareKeyboardController.current

  Column {
    // Search and Favourites are opened from their own pinned row, which already stays highlighted
    // as the title — a heading repeating it just read as a duplicate entry, so they get a bare count.
    if (!isSearch && source != ChannelListSource.Favourites) BackHeader(source.headerName, count = channels?.size, onBack = onBack)
    // Only Search gets a text field — everywhere else, Search itself is the one place to filter by
    // name, so a second per-list filter box was redundant.
    if (isSearch) {
      SearchField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        autoFocus = touchMode,
        onFocusChanged = onSearchFieldFocusChanged,
      )
    }
    if (channels == null) return@Column // still loading — no count or "nothing here" to show yet
    if ((isSearch || source == ChannelListSource.Favourites) && channels.isNotEmpty()) ChannelCount(channels.size)
    if (channels.isEmpty()) {
      EmptyListMessage(
        when {
          source == ChannelListSource.Favourites -> "No favourites yet.\nUse the ☆ next to any channel to add it here."
          isSearch && searchQuery.isBlank() -> "Type a channel name to search."
          isSearch -> "No channels match \"$searchQuery\"."
          else -> "No channels here."
        }
      )
    }
    LazyColumn(state = listState) {
      itemsIndexed(channels, key = { _, it -> it.id }) { i, channel ->
        ChannelRow(
          modifier = if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
          channel = channel,
          flag = flagByCountry[channel.countryCode].orEmpty(),
          bookmarked = channel.id in bookmarkedIds,
          playing = channel.id == currentChannelId,
          onClick = {
            if (isSearch) keyboard?.hide() // a tap on a row doesn't take focus from the field, so the keyboard would stay up over the video
            onSelectChannel(channel.id)
          },
          onToggleBookmark = { onToggleBookmark(channel) },
        )
      }
    }
  }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit, count: Int? = null) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    Modifier.fillMaxWidth()
      .panelRow(interaction, onClick = onBack)
      .heightIn(min = 52.dp)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(MeghIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    Text(
      title,
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    // Outside the title's Text so a long name ellipsizes without ever cutting the count off.
    if (count != null) {
      Text("  ($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
  }
}

@Composable
private fun ChannelCount(count: Int) {
  Text(
    if (count == 1) "1 channel" else "$count channels",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
  )
}

@Composable
private fun EmptyListMessage(text: String) {
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
  )
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, autoFocus: Boolean, onFocusChanged: (Boolean) -> Unit = {}) {
  // A text field otherwise swallows DPAD_DOWN as a (no-op) cursor-move key instead of letting
  // focus continue into the list below — intercept it here, before the field sees it, and hand
  // it to normal focus traversal instead. Standard gotcha wiring a plain text field into a D-pad UI.
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val fieldFocusRequester = remember { FocusRequester() }
  // On touch, opening Search means typing — go straight to the keyboard rather than making the user
  // tap the box too. Not when coming back to earlier results, though: the keyboard would cover them.
  LaunchedEffect(Unit) { if (autoFocus && value.isBlank()) fieldFocusRequester.requestFocus() }

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
    if (value.isEmpty()) Text("Search channels…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
      // Full width so a tap anywhere in the box (including on the placeholder) lands on the field.
      modifier = Modifier.fillMaxWidth().focusRequester(fieldFocusRequester),
    )
  }
}

@Composable
private fun PlainRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier
      .fillMaxWidth()
      .panelRow(interaction, onClick = onClick)
      .padding(20.dp, 14.dp),
  ) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val rowFocus = remember { FocusRequester() }
  val starFocus = remember { FocusRequester() }
  Row(
    modifier
      .fillMaxWidth()
      // D-pad: the star sits inside the row's own bounds, which directional search never looks
      // into — without an explicit link, Right from the row went nowhere and the star was touch-only.
      .focusRequester(rowFocus)
      .focusProperties { right = starFocus }
      .panelRow(interaction, selected = playing, onClick = onClick)
      .heightIn(min = 52.dp)
      .padding(start = 20.dp, end = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (playing) Text("▶  ", color = rowContentColor(selected = true), style = MaterialTheme.typography.bodyMedium)
      Text(
        "$flag  ${channel.displayName}",
        color = rowContentColor(playing),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (playing) FontWeight.Bold else null,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = Modifier.weight(1f, fill = false),
      )
    }
    val starInteraction = remember { MutableInteractionSource() }
    val starFocused by starInteraction.collectIsFocusedAsState()
    val starPressed by starInteraction.collectIsPressedAsState()
    val starHighlight = if (playing) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
    // 48dp: a comfortable finger target, not just a D-pad stop. A shaped background rather than
    // clip(): a clip would give every row its own render layer.
    Box(
      Modifier.size(48.dp)
        .background(if (starFocused || starPressed) starHighlight else Color.Transparent, RoundedCornerShape(8.dp))
        .focusRequester(starFocus)
        .focusProperties { left = rowFocus }
        .toggleable(
          value = bookmarked,
          onValueChange = { onToggleBookmark() },
          role = Role.Checkbox,
          interactionSource = starInteraction,
          indication = null,
        )
        .semantics { contentDescription = "${channel.displayName} favourite" },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        if (bookmarked) "★" else "☆",
        // On the cyan playing row both states go dark; the filled vs outlined shape carries it there.
        color =
          when {
            playing -> rowContentColor(selected = true)
            bookmarked -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        style = MaterialTheme.typography.titleLarge,
      )
    }
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
