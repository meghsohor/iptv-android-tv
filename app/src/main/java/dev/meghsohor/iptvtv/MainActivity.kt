package dev.meghsohor.iptvtv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.meghsohor.iptvtv.data.IptvRepository
import dev.meghsohor.iptvtv.data.db.IptvDatabase
import dev.meghsohor.iptvtv.theme.IPTVAndroidTVTheme

class MainActivity : ComponentActivity() {

  private val repository: IptvRepository by lazy { IptvRepository(IptvDatabase.getInstance(applicationContext)) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // The app is always dark, so system bar icons must be light regardless of the device theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    hideStatusBar()
    setContent {
      IPTVAndroidTVTheme {
        // Not a Surface: its full-screen fill would repaint the navy the window background already draws.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { MainNavigation(repository) }
      }
    }
  }

  // Re-applied on every focus gain: before Android 11 the hide is a view flag the system clears
  // when you leave the app, and a dialog window (the refresh one) can bring the bar back too.
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideStatusBar()
  }

  /** Status bar out of the way (a swipe brings it back briefly); the navigation bar stays so Back is always reachable. */
  private fun hideStatusBar() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.statusBars())
    }
  }
}
