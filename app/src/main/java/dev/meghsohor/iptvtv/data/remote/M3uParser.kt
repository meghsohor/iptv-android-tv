package dev.meghsohor.iptvtv.data.remote

/** One `#EXTINF` + URL pair from an iptv-org playlist. [tvgId] is "channelId@feedId". */
internal data class M3uEntry(val tvgId: String, val title: String, val url: String)

private val TVG_ID_REGEX = Regex("""tvg-id="([^"]*)"""")

/**
 * Parses an iptv-org-style `.m3u` playlist. Entries with a blank/missing tvg-id are skipped —
 * there's no channel/feed key to map them to.
 */
internal fun parseM3u(text: String): List<M3uEntry> {
  val entries = mutableListOf<M3uEntry>()
  var pendingTvgId: String? = null
  var pendingTitle: String? = null

  for (rawLine in text.lineSequence()) {
    val line = rawLine.trim()
    if (line.isEmpty()) continue
    if (line.startsWith("#EXTINF:")) {
      val tvgId = TVG_ID_REGEX.find(line)?.groupValues?.get(1).orEmpty()
      val title = line.substringAfterLast(',', missingDelimiterValue = "").trim()
      pendingTvgId = tvgId.ifEmpty { null }
      pendingTitle = title
    } else if (!line.startsWith("#")) {
      val tvgId = pendingTvgId
      if (tvgId != null) {
        entries.add(M3uEntry(tvgId = tvgId, title = pendingTitle.orEmpty(), url = line))
      }
      pendingTvgId = null
      pendingTitle = null
    }
    // other '#EXT*' directive lines are ignored
  }
  return entries
}
