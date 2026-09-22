package dev.meghsohor.iptvtv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val DATABASE_RAW = "https://raw.githubusercontent.com/iptv-org/database/master/data"
private const val IPTV_RAW = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams"
private const val IPTV_STREAMS_LISTING = "https://api.github.com/repos/iptv-org/iptv/contents/streams"

/** Fetches iptv-org's source data (see "Refresh mechanism" in the spec). Network-only, no parsing. */
class IptvOrgClient(private val http: OkHttpClient = OkHttpClient()) {

  private suspend fun getText(url: String): String =
    withContext(Dispatchers.IO) {
      http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("GET $url failed: HTTP ${response.code}")
        response.body?.string() ?: error("GET $url returned an empty body")
      }
    }

  suspend fun fetchChannelsCsv(): String = getText("$DATABASE_RAW/channels.csv")

  suspend fun fetchCategoriesCsv(): String = getText("$DATABASE_RAW/categories.csv")

  suspend fun fetchCountriesCsv(): String = getText("$DATABASE_RAW/countries.csv")

  /** Country codes (lowercase, e.g. "us") that have a compiled playlist, in listing order. */
  suspend fun fetchPlaylistCountryCodes(): List<String> {
    val json = getText(IPTV_STREAMS_LISTING)
    // Minimal extraction (no JSON dependency): pull "name": "xx.m3u" values in order.
    return Regex(""""name"\s*:\s*"([a-z0-9_]+)\.m3u"""").findAll(json).map { it.groupValues[1] }.toList()
  }

  /**
   * Fetches every country's compiled playlist, [maxConcurrent] at a time so a manual refresh
   * doesn't hammer GitHub's raw-content CDN with 300+ simultaneous requests.
   */
  suspend fun fetchAllPlaylists(countryCodes: List<String>, maxConcurrent: Int = 8): List<Pair<String, String>> =
    coroutineScope {
      val semaphore = Semaphore(maxConcurrent)
      countryCodes
        .map { cc -> async { semaphore.withPermit { cc to runCatching { getText("$IPTV_RAW/$cc.m3u") }.getOrDefault("") } } }
        .awaitAll()
        .filter { it.second.isNotBlank() }
    }
}
