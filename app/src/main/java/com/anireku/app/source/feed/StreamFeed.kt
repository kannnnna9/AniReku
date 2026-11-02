package com.anireku.app.source.feed

import com.anireku.app.core.model.Episode
import com.anireku.app.core.model.StreamLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On-disk schema of a user-supplied stream file. Documented in docs/sources.md. */
@Serializable
data class StreamFeed(
    val name: String = "My library",
    val entries: List<FeedEntry> = emptyList(),
)

@Serializable
data class FeedEntry(
    @SerialName("anilistId") val anilistId: Long,
    val episodes: List<FeedEpisode> = emptyList(),
)

@Serializable
data class FeedEpisode(
    val number: Int,
    val title: String? = null,
    val streams: List<FeedStream> = emptyList(),
)

@Serializable
data class FeedStream(
    val url: String,
    val quality: String = "auto",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Parses and validates a feed file. Parsing is strict about structure and lenient about missing values,
 * because the file is hand-written by the user.
 */
object FeedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): Result<StreamFeed> = runCatching {
        val feed = json.decodeFromString(StreamFeed.serializer(), raw)
        require(feed.entries.isNotEmpty()) { "The feed contains no entries." }
        feed.entries.forEach { entry ->
            require(entry.anilistId > 0) { "Every entry needs a positive anilistId." }
            entry.episodes.forEach { ep ->
                require(ep.number > 0) { "Episode numbers start at 1 (got ${ep.number})." }
                ep.streams.forEach { s ->
                    require(s.url.startsWith("http://") || s.url.startsWith("https://")) {
                        "Stream URL must be http(s): ${s.url}"
                    }
                }
            }
        }
        feed
    }

    fun toStreamLinks(episode: FeedEpisode): List<StreamLink> = episode.streams.map {
        StreamLink(url = it.url, quality = it.quality)
    }

    fun toEpisode(animeId: String, sourceId: String, episode: FeedEpisode): Episode = Episode(
        animeId = animeId,
        sourceId = sourceId,
        number = episode.number,
        title = episode.title ?: "Episode ${episode.number}",
        availableQualities = episode.streams.map { it.quality }.distinct(),
    )
}
