package dev.meghsohor.iptvtv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
 * close the side panel); otherwise it shows or hides the controls.
 * [controlsAllowed] = false keeps the controller hidden, since it would sit underneath the panel.
 * [controlsEdgeInset] keeps the bottom control row that far in from both screen edges.
 * [touchControls] makes play/pause tappable and adds volume/mute — TV remotes have keys for those.
 * [onPlaybackActiveChange] reports whether the stream is meant to be playing: not paused, not failed.
 * [onAllSourcesFailed] fires when the error screen appears for a reason other than the device being
 * offline, [onPlaying] each time the stream reaches playback.
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
  onPlaybackActiveChange: (Boolean) -> Unit,
  onAllSourcesFailed: () -> Unit,
  onPlaying: () -> Unit,
  onDeleteChannel: () -> Unit,
  modifier: Modifier = Modifier,
  overlayEndPadding: Dp = 0.dp,
  controlsEdgeInset: Dp = 0.dp,
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
  var playing by remember { mutableStateOf(true) }
  // Bumped on each play/pause by the user; the centre icon replays its animation per bump.
  var pulse by remember { mutableIntStateOf(0) }
  var volume by remember { mutableFloatStateOf(1f) }
  val currentStreamUrls by rememberUpdatedState(streamUrls)
  val currentOnTap by rememberUpdatedState(onTap)
  val currentControlsAllowed by rememberUpdatedState(controlsAllowed)
  val currentOnControlsVisibilityChange by rememberUpdatedState(onControlsVisibilityChange)
  val currentOnAllSourcesFailed by rememberUpdatedState(onAllSourcesFailed)
  val currentOnPlaying by rememberUpdatedState(onPlaying)
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
          if (!context.isOffline(error)) currentOnAllSourcesFailed()
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

        // Paused, the controls stay up and the picture is dimmed; playing, they time out as usual.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
          playing = playWhenReady
          val view = playerView ?: return
          view.findViewById<View>(Media3R.id.exo_controls_background)?.background = edgeScrim(view.resources.displayMetrics.density, dimmed = !playWhenReady)
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
          if (state == Player.STATE_READY && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            liveRejoins[0] = 0
            currentOnPlaying()
          }
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

  val playbackActive = playing && playbackError == null
  val currentOnPlaybackActiveChange by rememberUpdatedState(onPlaybackActiveChange)
  LaunchedEffect(playbackActive) { currentOnPlaybackActiveChange(playbackActive) }

  LaunchedEffect(muted, volume) { player.volume = if (muted) 0f else volume }

  fun setPlaying(play: Boolean) {
    if (player.playWhenReady == play) return
    player.playWhenReady = play
    pulse++
    if (currentControlsAllowed && playbackError == null) playerView?.showController() // so the new state is visible
  }

  LaunchedEffect(player, playerCommands) {
    playerCommands.collect { command ->
      when (command) {
        PlayerCommand.HideControls -> playerView?.hideController()
        PlayerCommand.ShowControls -> if (currentControlsAllowed && playbackError == null) playerView?.showController()
        PlayerCommand.Play -> setPlaying(true)
        PlayerCommand.Pause -> setPlaying(false)
        PlayerCommand.TogglePlayPause -> setPlaying(!player.playWhenReady)
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
          // Play/pause lives in the bottom row instead (see ControlsRow); the centre only gets PlayPausePulse.
          findViewById<View>(Media3R.id.exo_center_controls)?.visibility = View.GONE
          // No built-in setter for the time bar itself — hide it and the whole time block (position, "·", duration) directly.
          findViewById<View>(Media3R.id.exo_progress)?.visibility = View.GONE
          findViewById<View>(Media3R.id.exo_time)?.visibility = View.GONE

          // Media3's controller has no theme hook, so recolor the kept pieces directly.
          findViewById<ImageButton>(Media3R.id.exo_subtitle)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<ImageButton>(Media3R.id.exo_settings)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<View>(Media3R.id.exo_controls_background)?.background = edgeScrim(resources.displayMetrics.density, dimmed = false)
          findViewById<View>(Media3R.id.exo_bottom_bar)?.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, (controlsEdgeInset.value * resources.displayMetrics.density).toInt(), 0) // the bar is always LTR
          }

          setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
              controlsVisible = visibility == View.VISIBLE
              currentOnControlsVisibilityChange(controlsVisible)
            }
          )
          // With the menu open a tap only closes it; otherwise it shows or hides the controls.
          installTapHandler { view ->
            if (!currentOnTap()) {
              when {
                playbackError != null -> Unit // only the error screen's buttons mean anything there
                view.isControllerFullyVisible -> view.hideController()
                currentControlsAllowed -> view.showController()
              }
            }
          }
          playerView = this
        }
      },
      update = { view ->
        view.player = controllerPlayer
        // Also hidden under the error overlay: controls left up there would still take taps and Back.
        if (!controlsAllowed || playbackError != null) view.hideController()
      },
    )

    if (controlsVisible && playbackError == null) {
      ControlsRow(
        playing = playing,
        onTogglePlay = { setPlaying(!player.playWhenReady) },
        touchControls = touchControls,
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
        // Excluded from the back-swipe zone at the screen edge, which otherwise swallowed taps on play/pause.
        modifier = Modifier.align(Alignment.BottomStart).height(60.dp).systemGestureExclusion().padding(start = controlsEdgeInset),
      )
    }

    // End padding keeps it in the middle of the part of the video the side panel isn't covering.
    PlayPausePulse(playing = playing, trigger = pulse, modifier = Modifier.align(Alignment.Center).padding(end = overlayEndPadding))

    // No pointer handling, so it never blocks a tap.
    if (buffering && playbackError == null) {
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
        offline = remember(error) { context.isOffline(error) },
        onDelete = onDeleteChannel,
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
 * bottom (under the control row). The middle is clear while playing and [dimmed] while paused.
 */
private fun edgeScrim(density: Float, dimmed: Boolean): Drawable {
  fun fade(orientation: GradientDrawable.Orientation, alpha: Float) =
    GradientDrawable(orientation, intArrayOf(MeghBackground.copy(alpha = alpha).toArgb(), android.graphics.Color.TRANSPARENT))
  val dim = ColorDrawable(if (dimmed) MeghBackground.copy(alpha = 0.55f).toArgb() else android.graphics.Color.TRANSPARENT)
  return LayerDrawable(arrayOf(dim, fade(GradientDrawable.Orientation.TOP_BOTTOM, 0.7f), fade(GradientDrawable.Orientation.BOTTOM_TOP, 0.85f))).apply {
    setLayerGravity(1, Gravity.TOP)
    setLayerHeight(1, (120 * density).toInt())
    setLayerGravity(2, Gravity.BOTTOM)
    setLayerHeight(2, (160 * density).toInt())
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
 * Taps, ignoring drags. Consumes every touch, so PlayerView's own click-to-toggle never runs
 * alongside ours; touches on the controller's buttons are handled by those buttons and never reach this.
 */
@SuppressLint("ClickableViewAccessibility")
private fun PlayerView.installTapHandler(onTap: (PlayerView) -> Unit) {
  val view = this
  val detector =
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        // On tap-up, not "confirmed": with no double tap to wait for, that would only add ~300 ms.
        override fun onSingleTapUp(e: MotionEvent): Boolean {
          onTap(view)
          return true
        }
      },
    )
      // The listener also implements double-tap, which would swallow a quick second tap; nothing uses it.
      .apply { setOnDoubleTapListener(null) }
  setOnTouchListener { _, event ->
    detector.onTouchEvent(event)
    true
  }
}

@OptIn(ExperimentalMaterial3Api::class) // Slider's track slot
@Composable
private fun ControlsRow(
  playing: Boolean,
  onTogglePlay: () -> Unit,
  touchControls: Boolean,
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
    // On TV it only shows the state (OK and the media keys toggle it): not clickable, so never a D-pad stop.
    Box(
      Modifier.size(48.dp).clip(CircleShape).then(if (touchControls) Modifier.clickable(onClick = onTogglePlay) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (playing) MeghIcons.Pause else MeghIcons.Play,
        contentDescription = if (playing) "Pause" else "Play",
        tint = MeghCyan,
        modifier = Modifier.size(30.dp),
      )
    }
    if (!touchControls) return@Row
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
private fun PlaybackErrorOverlay(
  offline: Boolean,
  onRetry: () -> Unit,
  onDelete: () -> Unit,
  endPadding: Dp,
  focusRetry: Boolean,
  modifier: Modifier = Modifier,
) {
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
      Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverlayButton("Retry", onClick = onRetry, primary = true, modifier = Modifier.focusRequester(retryFocusRequester))
        // Offline says nothing about the channel, so no Delete there.
        if (!offline) OverlayButton("Delete channel", onClick = onDelete, primary = false)
      }
    }
  }
}

/** Mostly asked of the device, since one unreachable stream server times out exactly like a dead
 * connection does. But an HTTP error status means a server answered: never "offline". */
private fun Context.isOffline(error: PlaybackException) =
  error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && !hasInternet()

private fun Context.hasInternet(): Boolean {
  val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
  val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
  // VALIDATED too: Wi-Fi whose router has lost its uplink, or a captive portal, still claims INTERNET.
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun OverlayButton(label: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(8.dp)
  Text(
    label,
    color = if (primary) MeghBackground else Color.White,
    style = MaterialTheme.typography.titleSmall,
    modifier =
      modifier
        .clip(shape)
        .then(
          if (primary) Modifier.background(if (focused) MeghCyan else MeghCyan.copy(alpha = 0.85f))
          else Modifier.background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent).border(1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.4f), shape)
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
        .padding(horizontal = 24.dp, vertical = 10.dp),
  )
}

// Brand cyan, softened: at full strength it glared over the picture.
private val PulseColor = MeghCyan.copy(alpha = 0.75f)

/**
 * The centre "just played / just paused" effect: a thin-line icon that zooms in and fades out.
 * Nothing on first composition; each change of [trigger] replays it.
 */
@Composable
private fun PlayPausePulse(playing: Boolean, trigger: Int, modifier: Modifier = Modifier) {
  val progress = remember { Animatable(1f) }
  LaunchedEffect(trigger) {
    if (trigger == 0) return@LaunchedEffect
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis = 650, easing = LinearOutSlowInEasing))
  }
  if (progress.value >= 1f) return
  Canvas(
    modifier.size(96.dp).graphicsLayer {
      val p = progress.value
      scaleX = 0.7f + 0.5f * p
      scaleY = scaleX
      alpha = ((1f - p) / 0.7f).coerceAtMost(1f) // holds full strength for the first 30%
    }
  ) {
    val stroke = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
    drawCircle(MeghBackground.copy(alpha = 0.35f))
    drawCircle(PulseColor, radius = size.minDimension / 2 - stroke.width, style = stroke)
    val u = size.minDimension / 24f // icon drawn on a 24-unit grid
    if (playing) {
      val triangle = Path().apply {
        moveTo(9.5f * u, 7.5f * u)
        lineTo(17f * u, 12f * u)
        lineTo(9.5f * u, 16.5f * u)
        close()
      }
      drawPath(triangle, PulseColor, style = stroke)
    } else {
      drawRoundRect(PulseColor, topLeft = Offset(8.5f * u, 7.5f * u), size = Size(2.5f * u, 9f * u), cornerRadius = CornerRadius(u / 2), style = stroke)
      drawRoundRect(PulseColor, topLeft = Offset(13f * u, 7.5f * u), size = Size(2.5f * u, 9f * u), cornerRadius = CornerRadius(u / 2), style = stroke)
    }
  }
}
