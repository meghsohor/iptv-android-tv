package dev.meghsohor.meghtv.ui.main

sealed interface PanelState {
  data object CategoriesMenu : PanelState

  data object CountriesMenu : PanelState

  data class ChannelList(val source: ChannelListSource) : PanelState
}

sealed interface ChannelListSource {
  val headerName: String

  data object Favourites : ChannelListSource {
    override val headerName = "Favourites"
  }

  data object AllChannels : ChannelListSource {
    override val headerName = "All Channels"
  }

  data class Category(val id: String, val name: String) : ChannelListSource {
    override val headerName get() = name
  }

  data class Country(val code: String, val name: String) : ChannelListSource {
    override val headerName get() = name
  }

  data object Search : ChannelListSource {
    override val headerName = "Search"
  }
}

fun PanelState.backTarget(): PanelState =
  when (this) {
    PanelState.CategoriesMenu -> PanelState.CategoriesMenu
    PanelState.CountriesMenu -> PanelState.CategoriesMenu
    is PanelState.ChannelList ->
      when (source) {
        is ChannelListSource.Country -> PanelState.CountriesMenu
        else -> PanelState.CategoriesMenu
      }
  }
