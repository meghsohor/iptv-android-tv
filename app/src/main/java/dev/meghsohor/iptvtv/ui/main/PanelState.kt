package dev.meghsohor.iptvtv.ui.main

/** What the dynamic area of the single side panel is showing — see "Navigation" in the spec. */
sealed interface PanelState {
  /** The top-level list: All Channels, Countries, then every iptv-org category that has channels. */
  data object CategoriesMenu : PanelState

  /** Country names only, reached via the "Countries" row in [CategoriesMenu]. */
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

/**
 * Back pops exactly one level — not a generic stack, just a fixed parent per state (see the
 * "Navigation flow" diagram). Only [PanelState.ChannelList] backed by [ChannelListSource.Country]
 * has an intermediate parent (the country list); everything else falls straight back to Categories.
 */
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
