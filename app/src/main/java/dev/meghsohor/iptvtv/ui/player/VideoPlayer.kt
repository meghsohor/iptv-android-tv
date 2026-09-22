package dev.meghsohor.iptvtv.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Plays [streamUrls] in order, starting from index 0 (the manually-selected Source is already
 * moved to the front by the ViewModel). On playback error, automatically retries the next URL —
 * silent to the user, no interruption — per "Multiple sources per channel" in the spec.
 */
@Composable
fun VideoPlayer(streamUrls: List<String>, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val player = remember { ExoPlayer.Builder(context).build() }
  var urlIndex by remember(streamUrls) { mutableIntStateOf(0) }
  val currentStreamUrls by rememberUpdatedState(streamUrls)

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          if (urlIndex + 1 < currentStreamUrls.size) urlIndex += 1 // fall forward to the next source, silently
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(streamUrls) { urlIndex = 0 }

  LaunchedEffect(streamUrls, urlIndex) {
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

  AndroidView(
    modifier = modifier.fillMaxSize(),
    factory = { PlayerView(it).apply { useController = false } },
    update = { it.player = player },
  )
}
