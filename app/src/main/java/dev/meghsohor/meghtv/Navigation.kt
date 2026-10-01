package dev.meghsohor.meghtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.meghsohor.meghtv.data.MeghTVRepository
import dev.meghsohor.meghtv.ui.main.TvHomeScreen

@Composable
fun MainNavigation(repository: MeghTVRepository) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider { entry<Main> { TvHomeScreen(repository = repository, modifier = Modifier.fillMaxSize()) } },
  )
}
