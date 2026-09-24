package dev.meghsohor.iptvtv.ui.player

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import dev.meghsohor.iptvtv.theme.MeghBackground
import dev.meghsohor.iptvtv.theme.MeghCyan
import dev.meghsohor.iptvtv.theme.MeghOnSurfaceMuted

private val NetworkErrorCodes =
  setOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

/**
 * Plays [streamUrls] in order, starting from index 0 (the manually-selected Source is already
 * moved to the front by the ViewModel). On playback error, automatically retries the next URL —
 * silent to the user, no interruption — per "Multiple sources per channel" in the spec. Once every
 * URL has failed (including "no internet at all", which surfaces the same way), that's no longer
 * silent — an error overlay with a Retry button takes over.
 *
 * [onInteraction] fires on every tap on the video — the touch equivalent of a D-pad key, since
 * [PlayerView] swallows touches before they'd otherwise bubble up to an ancestor Compose
 * `clickable`.
 *
 * [channelId] identifies the channel independently of [streamUrls] — two different channels can
 * legitimately expose the exact same mirror list (duplicate source entries happen in iptv-org's
 * data), and a switch between them must still restart playback even though the URL list, compared
 * by value, wouldn't look like it changed.
 */
@Composable
fun VideoPlayer(channelId: String, streamUrls: List<String>, modifier: Modifier = Modifier, onInteraction: () -> Unit = {}) {
  val context = LocalContext.current
  val player = remember { ExoPlayer.Builder(context).build() }
  // Unkeyed (not `remember(streamUrls)`): the error listener below is installed once, in a
  // DisposableEffect keyed on `player`, and closes over these state objects at that point. If a
  // channel switch replaced them with fresh ones, the listener would keep mutating an orphaned
  // pair nothing reads any more — fallback and the error overlay would silently stop working
  // after the very first switch. LaunchedEffect(channelId, streamUrls) below resets their values instead.
  var urlIndex by remember { mutableIntStateOf(0) }
  var retryTick by remember(channelId, streamUrls) { mutableIntStateOf(0) }
  var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
  val currentStreamUrls by rememberUpdatedState(streamUrls)
  val currentOnInteraction by rememberUpdatedState(onInteraction)

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          if (urlIndex + 1 < currentStreamUrls.size) {
            urlIndex += 1 // fall forward to the next source, silently
          } else {
            playbackError = error // every mirror failed (or there was only ever one) — stop hiding it
          }
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(channelId, streamUrls) {
    urlIndex = 0
    playbackError = null
  }

  LaunchedEffect(channelId, streamUrls, urlIndex, retryTick) {
    val url = streamUrls.getOrNull(urlIndex)
    if (url == null) {
      player.stop()
      player.clearMediaItems()
    } else {
      player.setMediaItem(MediaItem.fromUri(url))
      player.prepare()
      player.playWhenReady = true
    }
  }

  Box(modifier) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        PlayerView(it).apply {
          useController = true
          // Live channels only — no seeking, no next/previous item to step to.
          setShowRewindButton(false)
          setShowFastForwardButton(false)
          setShowPreviousButton(false)
          setShowNextButton(false)
          setShowSubtitleButton(true)
          // No built-in setter for the time bar itself — hide it and its position/duration labels directly.
          findViewById<View>(Media3R.id.exo_progress)?.visibility = View.GONE
          findViewById<View>(Media3R.id.exo_position)?.visibility = View.GONE
          findViewById<View>(Media3R.id.exo_duration)?.visibility = View.GONE

          // Media3's controller has no theme hook — it's plain white/gray by default — so recolor
          // the pieces we kept directly, to match the rest of the app's brand palette. The
          // PlayPause style sets no background of its own (just the icon), so the "white circle"
          // seen before this was really the Android theme's default ImageButton background —
          // tinting *that* came out as a muddy dark blend, so it gets replaced outright instead.
          findViewById<ImageButton>(Media3R.id.exo_play_pause)?.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(MeghCyan.toArgb()) }
            imageTintList = ColorStateList.valueOf(MeghBackground.toArgb())
          }
          findViewById<ImageButton>(Media3R.id.exo_subtitle)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<ImageButton>(Media3R.id.exo_settings)?.imageTintList = ColorStateList.valueOf(MeghCyan.toArgb())
          findViewById<View>(Media3R.id.exo_controls_background)?.setBackgroundColor(MeghBackground.copy(alpha = 0.55f).toArgb())
          findViewById<View>(Media3R.id.exo_bottom_bar)?.setBackgroundColor(MeghBackground.copy(alpha = 0.9f).toArgb())

          // Not a ControllerVisibilityListener: that only fires when the controller transitions
          // TO visible, so a tap that *hides* an already-visible controller — still a real
          // interaction — would never reset the panel's timer. Watch touches directly instead,
          // returning false so PlayerView's own show/hide handling still runs normally.
          setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) currentOnInteraction()
            false
          }
        }
      },
      update = { it.player = player },
    )

    playbackError?.let { error ->
      PlaybackErrorOverlay(
        isNetworkError = error.errorCode in NetworkErrorCodes,
        onRetry = {
          urlIndex = 0
          playbackError = null
          retryTick++ // forces the LaunchedEffect to re-run even when urlIndex was already 0
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun PlaybackErrorOverlay(isNetworkError: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  val retryFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { retryFocusRequester.requestFocus() } // jump straight to Retry — this overlay is the only thing to do here

  Box(modifier.background(MeghBackground.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        if (isNetworkError) "No internet connection" else "This channel isn't available right now",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        if (isNetworkError) "Check your network and try again." else "All of its sources failed to load.",
        color = MeghOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
      RetryButton(onClick = onRetry, modifier = Modifier.focusRequester(retryFocusRequester))
    }
  }
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
