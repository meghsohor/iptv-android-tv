package dev.meghsohor.iptvtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.meghsohor.iptvtv.data.IptvRepository
import dev.meghsohor.iptvtv.ui.main.TvHomeScreen

@Composable
fun MainNavigation(repository: IptvRepository) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider { entry<Main> { TvHomeScreen(repository = repository, modifier = Modifier.fillMaxSize()) } },
  )
}
