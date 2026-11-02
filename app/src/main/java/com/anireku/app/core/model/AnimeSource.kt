package com.anireku.app.core.model

/** A source of anime data. Everything the UI knows about content comes through one of these. */
interface AnimeSource {
    val id: String
    val displayName: String
    val capabilities: Set<Capability>

    suspend fun search(query: String, page: Int = 1): PagedResult<AnimeSummary>
    suspend fun trending(page: Int = 1): PagedResult<AnimeSummary>
    suspend fun seasonal(season: Season, year: Int, page: Int = 1): PagedResult<AnimeSummary>
    suspend fun byGenre(genre: String, page: Int = 1): PagedResult<AnimeSummary>
    suspend fun byStudio(studioId: String, page: Int = 1): PagedResult<AnimeSummary>
    suspend fun detail(animeId: String): AnimeDetail
    suspend fun episodes(animeId: String): List<Episode>
    /** Streams a source can serve for this episode. Empty is a valid answer. */
    suspend fun streams(episode: Episode): List<StreamLink>
}

enum class Capability { CATALOGUE, EPISODES, STREAMS }

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val hasNextPage: Boolean,
)

data class AnimeSummary(
    val id: String,
    val sourceId: String,
    val title: String,
    val secondaryTitle: String? = null,
    val coverUrl: String? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val isAdult: Boolean = false,
)

data class AnimeDetail(
    val summary: AnimeSummary,
    val synopsis: String?,
    val bannerUrl: String?,
    val genres: List<String>,
    val studios: List<String>,
    val seasonYear: Int?,
    val airingStatus: String?,
    val totalEpisodes: Int?,
    val trailerUrl: String? = null,
)

data class Episode(
    val animeId: String,
    val sourceId: String,
    val number: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val airDate: String? = null,
    /** Higher resolution than the app knows about; used only for labels. */
    val availableQualities: List<String> = emptyList(),
)

data class StreamLink(
    val url: String,
    val quality: String,
    val mimeType: String? = null,
    val isHls: Boolean = url.contains(".m3u8", ignoreCase = true),
)

data class Season(val name: String, val year: Int)

class SourceException(
    message: String,
    cause: Throwable? = null,
    val reason: Reason = Reason.OTHER,
) : Exception(message, cause) {

    enum class Reason { NETWORK, RATE_LIMITED, NOT_FOUND, PAYLOAD, OTHER }
}
