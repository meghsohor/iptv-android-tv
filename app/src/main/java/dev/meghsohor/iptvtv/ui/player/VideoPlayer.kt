package dev.meghsohor.iptvtv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import dev.meghsohor.iptvtv.theme.MeghBackground
import dev.meghsohor.iptvtv.theme.MeghCyan
import dev.meghsohor.iptvtv.theme.MeghOnSurfaceMuted
import dev.meghsohor.iptvtv.ui.MeghIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val ControlsAutoHideMs = 5000

/** Consecutive live-edge rejoins before a stream is treated as failing rather than just paused too long. */
private const val MaxLiveRejoins = 3

/** Commands from outside the video surface — a TV remote's keys, or Back. */
enum class PlayerCommand { TogglePlayPause, Play, Pause, ShowControls, HideControls }

/**
 * Plays [streamUrls] in order, starting from index 0 (the manually-selected Source is already
 * moved to the front by the ViewModel). On playback error, automatically retries the next URL —
 * silent to the user, no interruption — per "Multiple sources per channel" in the spec. Once every
 * URL has failed (including "no internet at all", which surfaces the same way), that's no longer
 * silent — an error overlay with a Retry button takes over.
 *
 * A tap on the video goes to [onTap] first — returning true means the caller used it (e.g. to
 * close the side panel); otherwise a tap on visible controls plays/pauses, and on bare video shows them.
 * [controlsAllowed] = false keeps the controller hidden, since it would sit underneath the panel.
 * [touchControls] adds the on-screen volume/mute — TV remotes have hardware volume for that.
 *
 * [channelId] identifies the channel independently of [streamUrls] — two different channels can
 * legitimately expose the exact same mirror list (duplicate source entries happen in iptv-org's
 * data), and a switch between them must still restart playback even though the URL list, compared
 * by value, wouldn't look like it changed.
 */
@Composable
fun VideoPlayer(
  channelId: String,
  streamUrls: List<String>,
  controlsAllowed: Boolean,
  touchControls: Boolean,
  onTap: () -> Boolean,
  onControlsVisibilityChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  overlayEndPadding: Dp = 0.dp,
  playerCommands: Flow<PlayerCommand> = emptyFlow(),
) {
  val context = LocalContext.current
  val player = remember {
    // Decoder fallback: low-end TV chips sometimes fail to open their preferred (hardware) decoder
    // for a stream's profile — try the next one instead of failing the whole source.
    ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  }
  // What the controller sees: playback speed is meaningless on a live stream, so drop it from the settings menu.
  val controllerPlayer = remember(player) {
    object : ForwardingPlayer(player) {
      override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().remove(Player.COMMAND_SET_SPEED_AND_PITCH).build()

      override fun isCommandAvailable(command: Int): Boolean =
        command != Player.COMMAND_SET_SPEED_AND_PITCH && super.isCommandAvailable(command)
    }
  }
  // Unkeyed (not `remember(streamUrls)`): the error listener below is installed once, in a
  // DisposableEffect keyed on `player`, and closes over these state objects at that point. If a
  // channel switch replaced them with fresh ones, the listener would keep mutating an orphaned
  // pair nothing reads any more — fallback and the error overlay would silently stop working
  // after the very first switch. The load effect below resets their values on a switch instead.
  var attempt by remember { mutableStateOf(SourceAttempt()) }
  var retryTick by remember(channelId, streamUrls) { mutableIntStateOf(0) }
  var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
  var keepScreenOn by remember { mutableStateOf(false) }
  var buffering by remember { mutableStateOf(false) }
  var controlsVisible by remember { mutableStateOf(false) }
  var playerView by remember { mutableStateOf<PlayerView?>(null) }
  var muted by remember { mutableStateOf(false) }
  var volume by remember { mutableFloatStateOf(1f) }
  val currentStreamUrls by rememberUpdatedState(streamUrls)
  val currentOnTap by rememberUpdatedState(onTap)
  val currentControlsAllowed by rememberUpdatedState(controlsAllowed)
  val currentOnControlsVisibilityChange by rememberUpdatedState(onControlsVisibilityChange)
  val liveRejoins = remember { intArrayOf(0) }

  // Each change to `attempt` relaunches the load effect; a final failure leaves it alone, so
  // nothing reloads behind the error overlay.
  fun onSourceFailed(error: PlaybackException) {
    val url = currentStreamUrls.getOrNull(attempt.index)
    attempt =
      when {
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !attempt.asHls && url != null && !url.looksLikeHls() ->
          attempt.copy(asHls = true)
        attempt.index + 1 < currentStreamUrls.size -> SourceAttempt(attempt.index + 1) // next mirror, silently
        else -> {
          playbackError = error // every mirror failed (or there was only ever one) — stop hiding it
          return
        }
      }
  }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          // Paused longer than the stream keeps in its live window: nothing wrong with the source,
          // just rejoin at the live edge rather than failing over to the next one.
          if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && liveRejoins[0] < MaxLiveRejoins) {
            liveRejoins[0]++
            player.seekToDefaultPosition()
            player.prepare()
            return
          }
          onSourceFailed(error)
        }

        // Paused, the controls stay up (so the next tap resumes); playing, they time out as usual.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
          val view = playerView ?: return
          view.controllerShowTimeoutMs = if (playWhenReady) ControlsAutoHideMs else 0
          if (view.isControllerFullyVisible) view.showController() // re-arm with the new timeout
        }

        // Most IPTV streams have no captions, and Media3 would show a permanently greyed-out CC button for them.
        override fun onTracksChanged(tracks: Tracks) {
          playerView?.setShowSubtitleButton(tracks.containsType(C.TRACK_TYPE_TEXT))
        }

        override fun onEvents(player: Player, events: Player.Events) {
          val state = player.playbackState
          keepScreenOn = player.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED
          buffering = state == Player.STATE_BUFFERING
          if (state == Player.STATE_READY) liveRejoins[0] = 0
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  // Media3's PlayerView never holds the screen awake itself — without this a phone locks mid-stream.
  val hostView = LocalView.current
  DisposableEffect(keepScreenOn) {
    hostView.keepScreenOn = keepScreenOn
    onDispose { hostView.keepScreenOn = false }
  }

  // Live TV has nothing to resume from: drop the connection while backgrounded, rejoin at the live edge on return.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, player) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> player.stop()
        Lifecycle.Event.ON_START ->
          if (player.mediaItemCount > 0 && player.playbackState == Player.STATE_IDLE && playbackError == null) {
            player.seekToDefaultPosition()
            player.prepare()
          }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(muted, volume) { player.volume = if (muted) 0f else volume }

  LaunchedEffect(player, playerCommands) {
    playerCommands.collect { command ->
      when (command) {
        PlayerCommand.HideControls -> playerView?.hideController()
        PlayerCommand.ShowControls -> if (currentControlsAllowed && playbackError == null) playerView?.showController()
        PlayerCommand.Play, PlayerCommand.Pause, PlayerCommand.TogglePlayPause -> {
          player.playWhenReady =
            when (command) {
              PlayerCommand.Play -> true
              PlayerCommand.Pause -> false
              else -> !player.playWhenReady
            }
          if (currentControlsAllowed) playerView?.showController() // so the new play/pause state is visible
        }
      }
    }
  }

  // Which channel attempt/playbackError currently belong to. Resetting them here, inside the one
  // load effect, means a switch loads the new channel exactly once, at its first mirror.
  val loadedFor = remember { arrayOfNulls<Pair<String, List<String>>>(1) }
  LaunchedEffect(channelId, streamUrls, attempt, retryTick) {
    val key = channelId to streamUrls
    if (loadedFor[0] != key) {
      loadedFor[0] = key
      playbackError = null
      liveRejoins[0] = 0
      if (attempt != SourceAttempt()) {
        attempt = SourceAttempt() // relaunches this effect, at the first mirror
        return@LaunchedEffect
      }
    }
    val url = streamUrls.getOrNull(attempt.index)
    if (url == null) {
      player.stop()
      player.clearMediaItems()
    } else {
      // A fresh decoder per stream: ExoPlayer would otherwise reuse the running one across channels,
      // and some decoders (the emulator's, some cheap TV chipsets) then paint a lower-resolution
      // channel unscaled into the old channel's larger buffers, leaving its last frame showing around it.
      player.stop()
      // If a stream's format has no module in this build, Media3 throws from setMediaItem instead of
      // reporting a playback error — how every DASH channel crashed the app before its module was added.
      try {
        player.setMediaItem(MediaItem.Builder().setUri(url).apply { if (attempt.asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build())
        player.prepare()
      } catch (e: IllegalStateException) {
        onSourceFailed(PlaybackException("Unsupported stream format", e, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        return@LaunchedEffect
      }
      player.playWhenReady = true
    }
  }

  // Touching our own Compose controls doesn't reach Media3, so its hide timer needs a nudge.
  fun keepControlsAlive() {
    playerView?.takeIf { it.isControllerFullyVisible }?.showController()
  }

  Box(modifier) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        PlayerView(it).apply {
          useController = true
          // The controller grabs D-pad focus for its play/pause button whenever it shows; from then
          // on remote keys went to Media3 instead of the app (OK did nothing, arrows no longer opened
          // the panel). Keep it out of the focus chain — the app maps the remote keys itself.
          descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
          isFocusable = false
          controllerAutoShow = false
          controllerShowTimeoutMs = ControlsAutoHideMs
          // Animated, Media3 keeps the controller VISIBLE (and says so to its visibility listener) for
          // ~2 s after the bars have gone — so Back and OK acted on controls nobody could see.
          setControllerAnimationEnabled(false)
          // Live channels only — no seeking, no next/previous item to step to.
          setShowRewindButton(false)
          setShowFastForwardButton(false)
          setShowPreviousButton(false)
          setShowNextButton(false)
          setShowSubtitleButton(false) // until the stream turns out to carry captions — see onTracksChanged
          // No built-in setter for the time bar itself — hide it and the whole time block (position, "·", duration) directly.
          findViewById<View>(Media3R.id.exo_progress)?.visibility = View.GONE
          findViewById<View>(Media3R.id.exo_time)?.visibility = View.GONE

          // Media3's controller has no theme hook, so recolor the kept pieces directly. PlayPause
          // has no background of its own (the theme's default ImageButton one showed through and
          // tinted muddy), so it gets an explicit circle instead.
          findViewById<ImageButton>(Media3R.id.exo_play_pause)?.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(MeghCyan.toArgb()) }
            imageTintList = ColorStateList.valueOf(MeghBackground.toArgb())
          }
          findViewById<ImageButton>(Media3R.id.exo_subtitle)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<ImageButton>(Media3R.id.exo_settings)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<View>(Media3R.id.exo_controls_background)?.background = edgeScrim(resources.displayMetrics.density)
          findViewById<View>(Media3R.id.exo_bottom_bar)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

          setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
              controlsVisible = visibility == View.VISIBLE
              currentOnControlsVisibilityChange(controlsVisible)
            }
          )
          fun togglePlayPause(view: PlayerView) {
            player.playWhenReady = !player.playWhenReady
            if (currentControlsAllowed) view.showController() // so the new play/pause state is visible
          }
          // With the menu open a tap (or double tap) only closes it; otherwise a tap on the controls
          // overlay plays/pauses, on bare video brings the controls up, and a double tap plays/pauses.
          installTapHandler(
            onTap = { view ->
              if (!currentOnTap()) {
                when {
                  playbackError != null -> Unit // only Retry means anything on the error screen
                  view.isControllerFullyVisible -> togglePlayPause(view)
                  currentControlsAllowed -> view.showController()
                }
              }
            },
            // With the menu open, a double tap is two taps on the video: it closes the menu, nothing more.
            onDoubleTap = { view -> if (!currentOnTap() && playbackError == null) togglePlayPause(view) },
          )
          playerView = this
        }
      },
      update = { view ->
        view.player = controllerPlayer
        // Also hidden under the error overlay: controls left up there would still take taps and Back.
        if (!controlsAllowed || playbackError != null) view.hideController()
      },
    )

    if (touchControls && controlsVisible && playbackError == null) {
      VolumeControls(
        muted = muted || volume == 0f,
        volume = volume,
        onToggleMute = {
          if (muted || volume == 0f) {
            muted = false
            if (volume == 0f) volume = 1f
          } else {
            muted = true
          }
        },
        onVolumeChange = {
          volume = it
          if (it > 0f) muted = false
        },
        onTouch = ::keepControlsAlive,
        // Sits in the left half of Media3's 60dp bottom bar, where its (hidden) time labels would be.
        modifier = Modifier.align(Alignment.BottomStart).height(60.dp).padding(start = 8.dp),
      )
    }

    // No pointer handling, so it never blocks a tap; hidden while the controller's own centre button is up.
    if (buffering && playbackError == null && !controlsVisible) {
      CircularProgressIndicator(
        color = MeghCyan,
        strokeWidth = 3.dp,
        // End padding shifts it to the centre of the part of the video the side panel isn't covering.
        modifier = Modifier.align(Alignment.Center).padding(end = overlayEndPadding).size(48.dp),
      )
    }

    playbackError?.let { error ->
      PlaybackErrorOverlay(
        // Mostly asked of the device, since one unreachable stream server times out exactly like a
        // dead connection does. But an HTTP error status means a server answered: never "offline".
        offline = remember(error) { error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && !context.hasInternet() },
        onRetry = {
          attempt = SourceAttempt()
          playbackError = null
          retryTick++ // forces the load effect to re-run even when attempt was already the first one
        },
        endPadding = overlayEndPadding,
        focusRetry = controlsAllowed,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * Netflix/Prime-style scrim behind the controls: dark at the top (under the channel badge) and the
 * bottom (under the control row), with the middle of the picture left clear rather than dimmed.
 */
private fun edgeScrim(density: Float): Drawable {
  fun fade(orientation: GradientDrawable.Orientation, alpha: Float) =
    GradientDrawable(orientation, intArrayOf(MeghBackground.copy(alpha = alpha).toArgb(), android.graphics.Color.TRANSPARENT))
  return LayerDrawable(arrayOf(fade(GradientDrawable.Orientation.TOP_BOTTOM, 0.7f), fade(GradientDrawable.Orientation.BOTTOM_TOP, 0.85f))).apply {
    setLayerGravity(0, Gravity.TOP)
    setLayerHeight(0, (120 * density).toInt())
    setLayerGravity(1, Gravity.BOTTOM)
    setLayerHeight(1, (160 * density).toInt())
  }
}

/**
 * Which mirror is being tried, and whether as forced HLS: Media3 picks the format from the URL's
 * extension, ~300 iptv-org URLs have none, and the HLS ones among them fail as "unrecognized file" —
 * such a mirror gets one more try, as HLS, before moving on.
 */
private data class SourceAttempt(val index: Int = 0, val asHls: Boolean = false)

private fun String.looksLikeHls() = substringBefore('?').contains(".m3u8", ignoreCase = true)

/**
 * Single tap (confirmed only once it can't be the first half of a double tap) and double tap,
 * ignoring drags. Consumes every touch, so PlayerView's own click-to-toggle never runs alongside
 * ours; touches on the controller's buttons are handled by those buttons and never reach this.
 */
@SuppressLint("ClickableViewAccessibility")
private fun PlayerView.installTapHandler(onTap: (PlayerView) -> Unit, onDoubleTap: (PlayerView) -> Unit) {
  val view = this
  val detector =
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
          onTap(view)
          return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
          onDoubleTap(view)
          return true
        }
      },
    )
  setOnTouchListener { _, event ->
    detector.onTouchEvent(event)
    true
  }
}

@OptIn(ExperimentalMaterial3Api::class) // Slider's track slot
@Composable
private fun VolumeControls(
  muted: Boolean,
  volume: Float,
  onToggleMute: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onTouch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnTouch by rememberUpdatedState(onTouch)
  Row(
    modifier.pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          awaitPointerEvent(PointerEventPass.Initial)
          currentOnTouch()
        }
      }
    },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onToggleMute),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (muted) MeghIcons.VolumeOff else MeghIcons.VolumeUp,
        contentDescription = if (muted) "Unmute" else "Mute",
        tint = MeghCyan,
      )
    }
    val colors = SliderDefaults.colors(thumbColor = MeghCyan, activeTrackColor = MeghCyan, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    // Material 3's default slider is a chunky 16dp track with a bar thumb — far too heavy over video.
    Slider(
      value = if (muted) 0f else volume,
      onValueChange = onVolumeChange,
      modifier = Modifier.width(140.dp),
      colors = colors,
      thumb = { Box(Modifier.size(14.dp).background(MeghCyan, CircleShape)) },
      track = { sliderState ->
        SliderDefaults.Track(
          sliderState = sliderState,
          colors = colors,
          drawStopIndicator = null,
          thumbTrackGapSize = 0.dp,
          modifier = Modifier.height(4.dp),
        )
      },
    )
  }
}

@Composable
private fun PlaybackErrorOverlay(offline: Boolean, onRetry: () -> Unit, endPadding: Dp, focusRetry: Boolean, modifier: Modifier = Modifier) {
  val retryFocusRequester = remember { FocusRequester() }
  // Straight to Retry when it's the only thing on screen — but not while the side panel is open,
  // where it would pull D-pad focus out of the list being browsed.
  LaunchedEffect(focusRetry) { if (focusRetry) retryFocusRequester.requestFocus() }

  // endPadding keeps the message clear of the side panel when it's open over the video.
  Box(modifier.background(MeghBackground.copy(alpha = 0.92f)).padding(end = endPadding), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        if (offline) "No internet connection" else "This channel isn't available right now",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        if (offline) "Check your network and try again." else "All of its sources failed to load.",
        color = MeghOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
      RetryButton(onClick = onRetry, modifier = Modifier.focusRequester(retryFocusRequester))
    }
  }
}

private fun Context.hasInternet(): Boolean {
  val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
  val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
  // VALIDATED too: Wi-Fi whose router has lost its uplink, or a captive portal, still claims INTERNET.
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Text(
    "Retry",
    color = MeghBackground,
    style = MaterialTheme.typography.titleSmall,
    modifier =
      modifier
        .padding(top = 8.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (focused) MeghCyan else MeghCyan.copy(alpha = 0.85f))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
        .padding(horizontal = 24.dp, vertical = 10.dp),
  )
}
