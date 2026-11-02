package com.anireku.app.source.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GraphQlRequest(val query: String, val variables: Map<String, String> = emptyMap())

@Serializable
internal data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null)

@Serializable
internal data class GraphQlError(val message: String) {
    // AniList returns HTTP 200 with errors when rate limited or the query is rejected.
    fun isRateLimit() = message.contains("Too Many Requests", true)
}

@Serializable
internal data class PageData(val Page: Page)

@Serializable
internal data class Page(
    val pageInfo: PageInfo,
    val media: List<Media> = emptyList(),
)

@Serializable
internal data class PageInfo(val currentPage: Int?, val hasNextPage: Boolean?)

@Serializable
internal data class DetailData(val Media: Media)

@Serializable
internal data class Media(
    val id: Long,
    @SerialName("idMal") val malId: Long? = null,
    val title: MediaTitle,
    val coverImage: CoverImage? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: Int? = null,
    val status: String? = null,
    val format: String? = null,
    val averageScore: Int? = null,
    val seasonYear: Int? = null,
    val isAdult: Boolean? = null,
    val studios: StudioConnection? = null,
)

@Serializable
internal data class MediaTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
internal data class CoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@Serializable
internal data class StudioConnection(val edges: List<StudioEdge> = emptyList())

@Serializable
internal data class StudioEdge(val node: Studio)

@Serializable
internal data class Studio(val name: String)

internal object Queries {
    private const val MEDIA_FIELDS = """
        id
        idMal
        title { romaji english native }
        coverImage { extraLarge large color }
        episodes
        status
        format
        averageScore
        seasonYear
        isAdult
        genres
    """

    const val SEARCH = """
        query (${'$'}q: String, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 20) {
            pageInfo { currentPage hasNextPage }
            media(search: ${'$'}q, type: ANIME, sort: SEARCH_MATCH, isAdult: false) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    const val TRENDING = """
        query (${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 20) {
            pageInfo { currentPage hasNextPage }
            media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    const val SEASONAL = """
        query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 20) {
            pageInfo { currentPage hasNextPage }
            media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, isAdult: false,
                  sort: POPULARITY_DESC) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    const val BY_GENRE = """
        query (${'$'}genre: String, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 20) {
            pageInfo { currentPage hasNextPage }
            media(type: ANIME, genre: ${'$'}genre, isAdult: false, sort: POPULARITY_DESC) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    const val BY_STUDIO = """
        query (${'$'}q: String, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 20) {
            pageInfo { currentPage hasNextPage }
            media(type: ANIME, studioId: ${'$'}q, isAdult: false, sort: POPULARITY_DESC) {
              $MEDIA_FIELDS
            }
          }
        }
    """

    const val DETAIL = """
        query (${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            $MEDIA_FIELDS
            description(asHtml: false)
            bannerImage
            studios { edges { node { name } } }
          }
        }
    """
}
