package dev.meghsohor.iptvtv.ui.main

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.semantics.stateDescription
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
import dev.meghsohor.iptvtv.theme.MeghSurface
import dev.meghsohor.iptvtv.theme.MeghSurfaceVariant
import dev.meghsohor.iptvtv.ui.MeghIcons
import dev.meghsohor.iptvtv.ui.player.PlayerCommand
import dev.meghsohor.iptvtv.ui.player.VideoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val PanelWidth = 360.dp
private val PanelHandleWidth = 44.dp
private const val PanelAutoHideDelayMs = 7000L
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

  // Panel auto-hide: any arrow key, or a touch or scroll inside the panel, restarts the countdown;
  // arrow keys also reveal it. Channel Up/Down and volume touch neither. On touch it reopens from
  // its edge handle, and a tap outside it closes it straight away. Touches only stamp lastActivityAt
  // (no state), so a drag doesn't recompose the screen on every move event.
  var panelOpen by remember { mutableStateOf(true) }
  var activityTick by remember { mutableIntStateOf(0) }
  val lastActivityAt = remember { longArrayOf(SystemClock.uptimeMillis()) }
  var confirmRefresh by remember { mutableStateOf(false) }
  var confirmExit by remember { mutableStateOf(false) }
  var searchFieldFocused by remember { mutableStateOf(false) }
  var playbackActive by remember { mutableStateOf(false) }
  var menuChannel by remember { mutableStateOf<IndexedValue<ChannelEntity>?>(null) }
  var refocusAfterDelete by remember { mutableStateOf<IndexedValue<String>?>(null) }
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
    lastActivityAt[0] = SystemClock.uptimeMillis()
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
      // Asks first, then exits for real (finish()): left to the system, Android 12+ only moves the task
      // to the back, and reopening from the launcher would resume the last channel as if it autoplayed.
      else -> confirmExit = true
    }
  }

  // Frozen while a dialog is up (refresh, a channel's menu) and during a search, where a hide would
  // yank the keyboard or the results away. On touch that's the whole Search view; on TV only while
  // typing, since nothing but the timer closes the panel there and a result would play behind it.
  val refreshInProgress = state.refreshing || state.refreshMessage != null
  val searching = if (touchMode) isSearch(state.panel) else searchFieldFocused
  // Nothing to watch behind it, so no reason to hide: on touch while nothing is playing (none picked,
  // paused, failed); on TV only before a channel is picked, since only the timer can move the panel
  // off a paused picture or the error screen's buttons there.
  val nothingToWatch = if (touchMode) !(hasPlayer && playbackActive) else !hasPlayer
  val suppressAutoHide = refreshInProgress || confirmRefresh || menuChannel != null || confirmExit || searching || nothingToWatch
  LaunchedEffect(refreshInProgress) { if (refreshInProgress) panelOpen = true }

  LaunchedEffect(activityTick, suppressAutoHide, panelOpen) {
    if (suppressAutoHide || !panelOpen) return@LaunchedEffect
    lastActivityAt[0] = SystemClock.uptimeMillis() // each relaunch (activity, a freeze lifting, reopening) restarts the count
    while (true) {
      val remaining = lastActivityAt[0] + PanelAutoHideDelayMs - SystemClock.uptimeMillis()
      if (remaining <= 0) break
      delay(remaining)
    }
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
        // Focus on the video's own buttons (Retry, Delete channel): Left/Right move between them.
        if (!panelOpen && !rootSelfFocused && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) return@onPreviewKeyEvent false
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
        onPlaybackActiveChange = { playbackActive = it },
        onAllSourcesFailed = { viewModel.onPlaybackFailed(currentChannel.id) },
        onPlaying = { viewModel.onPlaybackWorked(currentChannel.id) },
        onDeleteChannel = {
          viewModel.onDeleteChannel(currentChannel.id)
          openPanel()
        },
        overlayEndPadding = if (panelOpen) PanelWidth else 0.dp,
        // Clear of the edge tab on the right, and the same on the left for balance.
        controlsEdgeInset = PanelHandleWidth + 8.dp,
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
        onActivity = { lastActivityAt[0] = SystemClock.uptimeMillis() },
        onSearchFieldFocusChanged = { searchFieldFocused = it },
        onRefresh = { confirmRefresh = true },
        onChannelMenu = { index, channel -> menuChannel = IndexedValue(index, channel) },
        refocusAfterDelete = refocusAfterDelete,
        onRefocused = { refocusAfterDelete = null },
        listState = listState,
        openedFrom = openedFrom,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(PanelWidth),
      )
    } else if (touchMode) {
      PanelHandle(onClick = ::openPanel, modifier = Modifier.align(Alignment.CenterEnd))
    }

    if (confirmRefresh) {
      ConfirmDialog(
        title = "Refresh channels?",
        message = "This downloads the latest channel list from iptv-org. Any channels you deleted will come back.",
        confirmLabel = "Refresh",
        touchMode = touchMode,
        onConfirm = {
          confirmRefresh = false
          viewModel.onRefresh()
        },
        onDismiss = { confirmRefresh = false },
      )
    }

    if (confirmExit) {
      ConfirmDialog(
        title = "Exit MeghTV?",
        message = null,
        confirmLabel = "Exit",
        touchMode = touchMode,
        onConfirm = { activity?.finish() },
        onDismiss = { confirmExit = false },
      )
    }

    menuChannel?.let { (index, channel) ->
      ChannelMenuDialog(
        channel = channel,
        bookmarked = channel.id in state.bookmarkedIds,
        failed = channel.id in state.failedIds,
        touchMode = touchMode,
        onToggleBookmark = {
          menuChannel = null
          viewModel.onToggleBookmark(channel)
        },
        onDelete = {
          menuChannel = null
          if (!touchMode) refocusAfterDelete = IndexedValue(index, channel.id)
          viewModel.onDeleteChannel(channel.id)
        },
        onDismiss = { menuChannel = null },
      )
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
      .size(width = PanelHandleWidth, height = 96.dp)
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
  onActivity: () -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onRefresh: () -> Unit,
  onChannelMenu: (index: Int, ChannelEntity) -> Unit,
  refocusAfterDelete: IndexedValue<String>?,
  onRefocused: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnActivity by rememberUpdatedState(onActivity)
  // D-pad: opening a list (or going back) removes the row that had focus, and the next key press
  // would then restart at the top of the panel. Put focus on the row we came back through, or the
  // first visible one, instead. Skipped when focus survived the switch (a pinned row was pressed).
  var panelHasFocus by remember { mutableStateOf(false) }
  val contentFocusRequester = remember { FocusRequester() }
  var focusIndex by remember(state.panel) { mutableIntStateOf(openedFrom[state.panel] ?: listState.firstVisibleItemIndex) }
  suspend fun focusRow(index: Int) {
    withFrameNanos {} // compose the list...
    withFrameNanos {} // ...and lay it out
    if (panelHasFocus) return
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    val shown = withTimeoutOrNull(2_000) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it } }
    if (shown != null) {
      withFrameNanos {}
      runCatching { contentFocusRequester.requestFocus() } // the row may have scrolled away meanwhile
    }
  }
  // A channel list has no rows until its query returns (listChannels == null); waiting for that
  // matters, since a revisited list's reused scroll state still describes the previous visit's rows.
  val contentReady = state.panel !is PanelState.ChannelList || state.listChannels != null
  // The panel it opened on is focused by the caller — skipped here until the panel first changes.
  val openedOn = remember { arrayOf<PanelState?>(state.panel) }
  LaunchedEffect(state.panel, contentReady) {
    if (openedOn[0] != state.panel) openedOn[0] = null
    if (touchMode || !contentReady || openedOn[0] != null) return@LaunchedEffect
    focusRow(focusIndex)
  }
  // A deleted row takes focus with it: once the list no longer has it, focus the row that took its place.
  LaunchedEffect(refocusAfterDelete, state.listChannels) {
    val (index, deletedId) = refocusAfterDelete ?: return@LaunchedEffect
    val rows = state.listChannels ?: return@LaunchedEffect
    if (rows.any { it.id == deletedId }) return@LaunchedEffect
    if (rows.isNotEmpty()) {
      focusIndex = index.coerceAtMost(rows.lastIndex)
      focusRow(focusIndex)
    }
    onRefocused()
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
      .drawBehind {
        drawRect(PanelBackground)
        drawRect(LineColor, size = size.copy(width = 1.dp.toPx())) // hairline where the panel meets the video
      }
      .onFocusChanged { panelHasFocus = it.hasFocus }
      // Observes (never consumes) every touch: restarts the auto-hide countdown, and registers the
      // panel as a touch target so a tap on empty space doesn't fall through to the video and close it.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            currentOnActivity()
          }
        }
      }
      .imePadding()
  ) {
    val compact = maxHeight < CompactPanelHeight
    Column(Modifier.fillMaxSize()) {
      // The pinned tabs share one darker band with a bottom border, split by thin dividers rather
      // than drawn as blocks, so they read as a separate section from the list below.
      val band = Modifier.fillMaxWidth().background(PinnedBand).drawBehind {
        drawRect(LineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
      }
      if (compact) {
        Row(band.height(52.dp).padding(bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
          PinnedRow("Refresh", MeghIcons.Refresh, onRefresh, compact = true, modifier = Modifier.weight(1f))
          PinnedDivider(vertical = true)
          PinnedRow("Search", MeghIcons.Search, viewModel::onPinnedSearch, selected = isSearch(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isSearch(state.panel))))
          PinnedDivider(vertical = true)
          PinnedRow("Favourites", MeghIcons.Star, viewModel::onPinnedFavourites, selected = isFavourites(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isFavourites(state.panel))))
          PinnedDivider(vertical = true)
          PinnedRow("Categories", MeghIcons.Grid, viewModel::onPinnedCategories, selected = isCategories(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isCategories(state.panel))))
        }
      } else {
        Column(band) {
          PinnedRow("Refresh Channels", MeghIcons.Refresh, onRefresh, modifier = Modifier.fillMaxWidth())
          PinnedDivider(vertical = false)
          PinnedRow("Search", MeghIcons.Search, viewModel::onPinnedSearch, selected = isSearch(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isSearch(state.panel))))
          PinnedDivider(vertical = false)
          PinnedRow("Favourites", MeghIcons.Star, viewModel::onPinnedFavourites, selected = isFavourites(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isFavourites(state.panel))))
          PinnedDivider(vertical = false)
          PinnedRow("Categories", MeghIcons.Grid, viewModel::onPinnedCategories, selected = isCategories(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isCategories(state.panel))))
        }
      }

      // Nothing until launch has picked the opening view, or Categories flashes up before Favourites.
      Box(Modifier.weight(1f).padding(start = RowGutter, end = RowGutter, top = 10.dp)) {
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
              failedIds = state.failedIds,
              currentChannelId = state.currentChannel?.id,
              onBack = { viewModel.onBack() },
              onSearchQueryChange = viewModel::onSearchQueryChange,
              onSelectChannel = viewModel::onSelectChannel,
              onToggleBookmark = viewModel::onToggleBookmark,
              onChannelMenu = onChannelMenu,
              onSearchFieldFocusChanged = onSearchFieldFocusChanged,
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

private val RowGutter = 10.dp
private val RowGap = 6.dp
private val RowRadius = 12.dp
private val PanelBackground = MeghSurface.copy(alpha = 0.97f)
// A step darker than the panel, same navy family.
private val PinnedBand = lerp(MeghSurface, MeghBackground, 0.4f)
private val RowFill = MeghSurfaceVariant.copy(alpha = 0.55f)
private val LineColor = Color.White.copy(alpha = 0.09f)

// A channel that didn't play last time: a fainter block and name, still readable.
private val DimmedRowFill = MeghSurfaceVariant.copy(alpha = 0.2f)
private val DimmedLineColor = Color.White.copy(alpha = 0.04f)
private const val DimmedTextAlpha = 0.5f

/**
 * Shared row treatment. A [block] row (the lists) is its own rounded block with a hairline border,
 * fainter when [dimmed]; otherwise (the pinned tabs) there's no fill until it's selected, pressed or focused. The active
 * row (selected tab, playing channel) is solid brand cyan — pair it with [rowContentColor] — a
 * pressed or focused row is lighter, and D-pad focus draws a 2dp ring (navy on the cyan row).
 * Drawn in one drawBehind, no clip or animation: each would cost per visible row on a low-end TV.
 */
@Composable
private fun Modifier.panelRow(
  interaction: MutableInteractionSource,
  selected: Boolean = false,
  block: Boolean = true,
  dimmed: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val focused by interaction.collectIsFocusedAsState()
  val pressed by interaction.collectIsPressedAsState()
  val colors = MaterialTheme.colorScheme
  val fill =
    when {
      selected -> colors.primary
      focused || pressed -> colors.surfaceVariant
      dimmed -> DimmedRowFill
      block -> RowFill
      else -> Color.Transparent
    }
  val ring = if (selected) colors.onPrimary else colors.primary
  return this.drawBehind {
      val radius = if (block) RowRadius.toPx() else 0f
      drawRoundRect(fill, cornerRadius = CornerRadius(radius))
      val width = (if (focused) 2.dp else 1.dp).toPx()
      val inset = Offset(width / 2, width / 2)
      val ringSize = Size(size.width - width, size.height - width)
      val corner = CornerRadius(radius - width / 2)
      when {
        focused -> drawRoundRect(ring, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
        block && !selected -> drawRoundRect(if (dimmed) DimmedLineColor else LineColor, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
      }
    }
    .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
    .focusable(interactionSource = interaction)
}

private val ThumbIdle = Color(0xFF2C3860)
private val ThumbActive = Color(0xFF4A5A8C)

/**
 * Where you are in a long list: a thin thumb in the gutter right of the rows, sized from the share
 * of the list on screen, a little brighter while scrolling. Rows are near-equal height, so their
 * average stands in for real offsets. Read in the draw phase only, so a scroll redraws, never recomposes.
 */
private fun Modifier.scrollIndicator(state: LazyListState): Modifier = drawWithContent {
  drawContent()
  val info = state.layoutInfo
  val visible = info.visibleItemsInfo
  if (visible.isEmpty() || !(state.canScrollForward || state.canScrollBackward)) return@drawWithContent
  val itemSize = visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing
  val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
  val content = itemSize * info.totalItemsCount
  val scrolled = visible.first().index * itemSize - visible.first().offset
  val thumb = (viewport * viewport / content).coerceIn(28.dp.toPx(), viewport)
  val top = (scrolled / (content - viewport)).coerceIn(0f, 1f) * (viewport - thumb)
  val width = 3.dp.toPx()
  drawRoundRect(
    if (state.isScrollInProgress) ThumbActive else ThumbIdle,
    topLeft = Offset(size.width + (RowGutter.toPx() - width) / 2, top),
    size = Size(width, thumb),
    cornerRadius = CornerRadius(width / 2),
  )
}

/** Between two pinned tabs: a short line, not full height, so the tabs read as one group. */
@Composable
private fun PinnedDivider(vertical: Boolean) {
  Box(if (vertical) Modifier.width(1.dp).height(24.dp).background(LineColor) else Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LineColor))
}

@Composable
private fun rowContentColor(selected: Boolean): Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

@Composable
private fun PinnedRow(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  compact: Boolean = false,
) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier
      .panelRow(interaction, selected, block = false, onClick = onClick)
      .then(if (compact) Modifier.fillMaxHeight() else Modifier.heightIn(min = 48.dp))
      .padding(horizontal = if (compact) 2.dp else 16.dp),
    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Compact tabs are too narrow for an icon and "Favourites" side by side.
    if (!compact) {
      Icon(icon, contentDescription = null, tint = if (selected) rowContentColor(true) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(14.dp))
    }
    Text(
      label,
      color = rowContentColor(selected),
      style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Normal,
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
  LazyColumn(Modifier.scrollIndicator(listState), state = listState, contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(RowGap)) {
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
    LazyColumn(Modifier.scrollIndicator(listState), state = listState, contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(RowGap)) {
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
  failedIds: Set<String>,
  currentChannelId: String?,
  onBack: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectChannel: (String) -> Unit,
  onToggleBookmark: (ChannelEntity) -> Unit,
  onChannelMenu: (index: Int, ChannelEntity) -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
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
          source == ChannelListSource.Favourites -> "No favourites yet.\nUse the star next to any channel to add it here."
          isSearch && searchQuery.isBlank() -> "Type a channel name to search."
          isSearch -> "No channels match \"$searchQuery\"."
          else -> "No channels here."
        }
      )
    }
    LazyColumn(
      Modifier.scrollIndicator(listState),
      state = listState,
      contentPadding = ListPadding,
      verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
      itemsIndexed(channels, key = { _, it -> it.id }) { i, channel ->
        ChannelRow(
          modifier = if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
          channel = channel,
          flag = flagByCountry[channel.countryCode].orEmpty(),
          bookmarked = channel.id in bookmarkedIds,
          playing = channel.id == currentChannelId,
          failed = channel.id in failedIds,
          onLongClick = {
            if (isSearch) keyboard?.hide()
            onChannelMenu(i, channel)
          },
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
    Modifier.padding(bottom = RowGap)
      .fillMaxWidth()
      .panelRow(interaction, onClick = onBack)
      .heightIn(min = 48.dp)
      .padding(horizontal = 14.dp),
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
    modifier = Modifier.padding(start = 6.dp, bottom = RowGap),
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
private fun SearchField(value: String, onValueChange: (String) -> Unit, autoFocus: Boolean, onFocusChanged: (Boolean) -> Unit) {
  // A text field otherwise swallows DPAD_DOWN as a (no-op) cursor-move key instead of letting
  // focus continue into the list below — intercept it here, before the field sees it, and hand
  // it to normal focus traversal instead. Standard gotcha wiring a plain text field into a D-pad UI.
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val fieldFocusRequester = remember { FocusRequester() }
  // On touch, opening Search means typing — go straight to the keyboard rather than making the user
  // tap the box too. Not when coming back to earlier results, though: the keyboard would cover them.
  LaunchedEffect(Unit) { if (autoFocus && value.isBlank()) fieldFocusRequester.requestFocus() }

  var focused by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(RowRadius)
  Box(
    Modifier.fillMaxWidth()
      .padding(bottom = RowGap)
      .background(MaterialTheme.colorScheme.surfaceVariant, shape)
      .border(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else LineColor, shape)
      .padding(14.dp, 12.dp)
      .onFocusChanged { // Box itself isn't focusable — BasicTextField below is
        focused = it.hasFocus
        onFocusChanged(it.hasFocus)
      }
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
      .heightIn(min = 48.dp)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
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
  failed: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
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
      .panelRow(interaction, selected = playing, dimmed = failed, onLongClick = onLongClick, onClick = onClick)
      .semantics { if (failed) stateDescription = "Not working" }
      .heightIn(min = 48.dp)
      .padding(start = 14.dp, end = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (playing) Icon(MeghIcons.Play, contentDescription = null, tint = rowContentColor(selected = true), modifier = Modifier.padding(end = 8.dp).size(12.dp))
      Text(
        "$flag  ${channel.displayName}",
        color = rowContentColor(playing),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (playing) FontWeight.Bold else null,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        // alpha(), not a text colour: a colour's alpha doesn't reach the flag emoji. Few rows are dimmed, so the layer is cheap.
        modifier = Modifier.weight(1f, fill = false).then(if (failed && !playing) Modifier.alpha(DimmedTextAlpha) else Modifier),
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
      Icon(
        if (bookmarked) MeghIcons.StarFilled else MeghIcons.Star,
        contentDescription = null,
        // On the cyan playing row both states go dark; the filled vs outlined shape carries it there.
        tint =
          when {
            playing -> rowContentColor(selected = true)
            bookmarked -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

private val ListPadding = PaddingValues(bottom = 12.dp)

private const val RefreshResultAutoCloseMs = 4000L

/** Shared frame for the app's dialogs: a rounded panel with the same hairline border as the rows. */
@Composable
private fun DialogCard(content: @Composable () -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Box(Modifier.widthIn(min = 300.dp, max = 420.dp).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, LineColor, shape)) { content() }
}

@Composable
private fun ConfirmDialog(
  title: String,
  message: String?,
  confirmLabel: String,
  touchMode: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  // Cancel first, so OK (or Back) pressed twice can't confirm by accident.
  val cancelFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onDismiss) {
    // Inside the dialog: its content is composed in its own window, which isn't attached before this runs.
    LaunchedEffect(Unit) { if (!touchMode) cancelFocus.requestFocus() }
    DialogCard {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        if (message != null) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
          DialogButton("Cancel", onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
          DialogButton(confirmLabel, onClick = onConfirm)
        }
      }
    }
  }
}

/** Long-press (or hold OK) on a channel. */
@Composable
private fun ChannelMenuDialog(
  channel: ChannelEntity,
  bookmarked: Boolean,
  failed: Boolean,
  touchMode: Boolean,
  onToggleBookmark: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  val firstFocus = remember { FocusRequester() }
  // Opened by holding OK: that key's repeats and its release arrive here next and would click the
  // first option. Keys count only from the first fresh press.
  var armed by remember { mutableStateOf(false) }
  Dialog(onDismissRequest = onDismiss) {
    LaunchedEffect(Unit) { if (!touchMode) firstFocus.requestFocus() } // inside the dialog, as in ConfirmDialog
    DialogCard {
      Column(
        Modifier.padding(16.dp).onPreviewKeyEvent { event ->
          if (!armed && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
          !armed
        },
        verticalArrangement = Arrangement.spacedBy(RowGap),
      ) {
        Text(
          channel.displayName,
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        if (failed) {
          Text("Didn't play last time", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        MenuOption(if (bookmarked) "Remove from favourites" else "Add to favourites", MeghIcons.Star, onToggleBookmark, Modifier.focusRequester(firstFocus))
        MenuOption("Delete channel", MeghIcons.Delete, onDelete, tint = MeghLive)
        Text(
          "A deleted channel comes back with the next refresh.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun MenuOption(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier.fillMaxWidth().panelRow(interaction, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(14.dp))
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
  }
}

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
    DialogCard {
      Column(
        Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        if (refreshing) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          Text("Refreshing channels…", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
        } else if (message != null) {
          Text(message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
          Box(Modifier.fillMaxWidth()) { DialogButton("Close", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) }
        }
      }
    }
  }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Text(
    label,
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
