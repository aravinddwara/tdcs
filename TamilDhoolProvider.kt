package com.tamildhool

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.DailymotionExtractor
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class TamilDhoolProvider : MainAPI() {
    override var mainUrl = "https://www.tamildhool.net"
    override var name = "TamilDhool"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)
    override val lang = "ta"

    // Channel mappings
    private val channels = mapOf(
        "Vijay TV" to "/vijay-tv/",
        "Sun TV" to "/sun-tv/",
        "Zee Tamil" to "/zee-tamil/",
        "Kalaignar TV" to "/kalaignar-tv/"
    )

    // Category mappings
    private val categories = mapOf(
        "serial" to "serial",
        "show" to "show"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        
        // Get latest episodes from each channel (Vijay TV first)
        val channelOrder = listOf("Vijay TV", "Sun TV", "Zee Tamil", "Kalaignar TV")
        
        for (channel in channelOrder) {
            val channelPath = channels[channel] ?: continue
            
            // Get serials from this channel
            val serialsUrl = "$mainUrl$channelPath${channel.toLowerCase().replace(" ", "-")}-serial/"
            val serialsDoc = app.get(serialsUrl).document
            
            val serials = serialsDoc.select("article.post").mapNotNull { element ->
                val title = element.selectFirst("h2 a")?.text() ?: return@mapNotNull null
                val href = element.selectFirst("h2 a")?.attr("href") ?: return@mapNotNull null
                val posterUrl = element.selectFirst("img")?.attr("src") ?: ""
                
                TvSeriesSearchResponse(
                    name = title,
                    url = href,
                    apiName = this.name,
                    type = TvType.TvSeries,
                    posterUrl = posterUrl
                )
            }.take(6) // Limit to 6 series per channel
            
            if (serials.isNotEmpty()) {
                items.add(HomePageList("$channel Serials", serials))
            }
        }
        
        // Get latest episodes across all channels
        val latestEpisodes = getLatestEpisodes().take(12)
        if (latestEpisodes.isNotEmpty()) {
            items.add(HomePageList("Latest Episodes", latestEpisodes))
        }
        
        return HomePageResponse(items)
    }

    private suspend fun getLatestEpisodes(): List<SearchResponse> {
        val episodes = mutableListOf<SearchResponse>()
        
        channels.forEach { (channelName, channelPath) ->
            try {
                val serialsUrl = "$mainUrl$channelPath${channelName.toLowerCase().replace(" ", "-")}-serial/"
                val serialsDoc = app.get(serialsUrl).document
                
                serialsDoc.select("article.post").take(3).forEach { element ->
                    val title = element.selectFirst("h2 a")?.text() ?: return@forEach
                    val href = element.selectFirst("h2 a")?.attr("href") ?: return@forEach
                    val posterUrl = element.selectFirst("img")?.attr("src") ?: ""
                    
                    // Get latest episode from this series
                    val seriesDoc = app.get(href).document
                    val latestEpisode = seriesDoc.select("article.post").firstOrNull()
                    
                    latestEpisode?.let { ep ->
                        val episodeTitle = ep.selectFirst("h2 a")?.text() ?: return@let
                        val episodeUrl = ep.selectFirst("h2 a")?.attr("href") ?: return@let
                        val episodePoster = ep.selectFirst("img")?.attr("src") ?: posterUrl
                        
                        episodes.add(
                            MovieSearchResponse(
                                name = episodeTitle,
                                url = episodeUrl,
                                apiName = this.name,
                                type = TvType.Movie,
                                posterUrl = episodePoster
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Continue with other channels if one fails
            }
        }
        
        return episodes
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        
        channels.forEach { (channelName, channelPath) ->
            try {
                val serialsUrl = "$mainUrl$channelPath${channelName.toLowerCase().replace(" ", "-")}-serial/"
                val doc = app.get(serialsUrl).document
                
                doc.select("article.post").forEach { element ->
                    val title = element.selectFirst("h2 a")?.text() ?: return@forEach
                    val href = element.selectFirst("h2 a")?.attr("href") ?: return@forEach
                    val posterUrl = element.selectFirst("img")?.attr("src") ?: ""
                    
                    if (title.contains(query, ignoreCase = true)) {
                        searchResults.add(
                            TvSeriesSearchResponse(
                                name = title,
                                url = href,
                                apiName = this.name,
                                type = TvType.TvSeries,
                                posterUrl = posterUrl
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Continue with other channels if one fails
            }
        }
        
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title")?.text() ?: "Unknown"
        val posterUrl = doc.selectFirst("img")?.attr("src") ?: ""
        
        // Check if this is a series page (contains multiple episodes)
        val episodes = doc.select("article.post").mapNotNull { element ->
            val episodeTitle = element.selectFirst("h2 a")?.text() ?: return@mapNotNull null
            val episodeUrl = element.selectFirst("h2 a")?.attr("href") ?: return@mapNotNull null
            val episodePoster = element.selectFirst("img")?.attr("src") ?: posterUrl
            
            // Extract date from episode title (format: "Show Name DD-MM-YYYY")
            val dateRegex = Regex("(\\d{2}-\\d{2}-\\d{4})")
            val dateMatch = dateRegex.find(episodeTitle)
            val episodeNumber = dateMatch?.groupValues?.get(1)?.let { dateStr ->
                try {
                    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val date = sdf.parse(dateStr)
                    date?.time?.toString() ?: "0"
                } catch (e: Exception) {
                    "0"
                }
            } ?: "0"
            
            Episode(
                data = episodeUrl,
                name = episodeTitle,
                episode = episodeNumber.toIntOrNull(),
                posterUrl = episodePoster
            )
        }
        
        return if (episodes.isNotEmpty()) {
            // Sort episodes by date (newest first)
            val sortedEpisodes = episodes.sortedByDescending { it.episode }
            
            TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                episodes = sortedEpisodes,
                posterUrl = posterUrl,
                plot = "Tamil TV Serial from TamilDhool"
            )
        } else {
            // Single episode
            MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = url,
                posterUrl = posterUrl,
                plot = "Tamil TV Episode from TamilDhool"
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // Look for Dailymotion embeds
        val dailymotionLinks = doc.select("iframe[src*='dailymotion.com']").mapNotNull { iframe ->
            iframe.attr("src")
        }
        
        // Also check for Dailymotion links in script tags or other elements
        val scriptContent = doc.select("script").joinToString(" ") { it.html() }
        val dailymotionRegex = Regex("(?:dailymotion\\.com/(?:video/|embed/)|dai\\.ly/)([a-zA-Z0-9]+)")
        val dailymotionMatches = dailymotionRegex.findAll(scriptContent)
        
        val allDailymotionUrls = mutableSetOf<String>()
        
        // Add iframe sources
        dailymotionLinks.forEach { url ->
            allDailymotionUrls.add(url)
        }
        
        // Add script-found IDs
        dailymotionMatches.forEach { match ->
            val videoId = match.groupValues[1]
            allDailymotionUrls.add("https://www.dailymotion.com/embed/video/$videoId")
        }
        
        // Extract links using Dailymotion extractor
        val dailymotionExtractor = DailymotionExtractor()
        
        allDailymotionUrls.forEach { url ->
            try {
                dailymotionExtractor.getUrl(url, referer = mainUrl)?.forEach { link ->
                    callback.invoke(link)
                }
            } catch (e: Exception) {
                // Continue with other links if one fails
            }
        }
        
        // Fallback: Look for other video sources
        if (allDailymotionUrls.isEmpty()) {
            // Look for other embedded players
            doc.select("iframe[src*='video'], iframe[src*='stream'], iframe[src*='player']").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    try {
                        loadExtractor(src, data, subtitleCallback, callback)
                    } catch (e: Exception) {
                        // Continue with other sources
                    }
                }
            }
        }
        
        return true
    }
}
