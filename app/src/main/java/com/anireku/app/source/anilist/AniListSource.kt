package com.anireku.app.source.anilist

import com.anireku.app.core.model.AnimeDetail
import com.anireku.app.core.model.AnimeSource
import com.anireku.app.core.model.AnimeSummary
import com.anireku.app.core.model.Capability
import com.anireku.app.core.model.Episode
import com.anireku.app.core.model.PagedResult
import com.anireku.app.core.model.Season
import com.anireku.app.core.model.SourceException
import com.anireku.app.core.model.StreamLink
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Catalogue + metadata source backed by AniList's public GraphQL API.
 *
 * It never returns streams: AniList publishes metadata, not video. With this source alone the app is a
 * tracker/browser; episodes get a playable URL only from a source that declares [Capability.STREAMS].
 */
class AniListSource(
    private val http: OkHttpClient = defaultClient(),
    private val endpoint: String = "https://graphql.anilist.co",
) : AnimeSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "AniList"
    override val capabilities: Set<Capability> = setOf(Capability.CATALOGUE, Capability.EPISODES)

    private val pageAdapter = GraphQlResponse.serializer(PageData.serializer())
    private val detailAdapter = GraphQlResponse.serializer(DetailData.serializer())
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val cache = object : LinkedHashMap<String, CachedEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntry>) = size > 64
    }

    private class CachedEntry(val payload: String, val at: Long)

    override suspend fun search(query: String, page: Int): PagedResult<AnimeSummary> =
        page(Queries.SEARCH, mapOf("q" to query, "page" to page.toString()))

    override suspend fun trending(page: Int): PagedResult<AnimeSummary> =
        page(Queries.TRENDING, mapOf("page" to page.toString()))

    override suspend fun seasonal(season: Season, year: Int, page: Int): PagedResult<AnimeSummary> =
        page(
            Queries.SEASONAL,
            mapOf(
                "season" to season.name.uppercase(),
                "year" to year.toString(),
                "page" to page.toString(),
            ),
        )

    override suspend fun byGenre(genre: String, page: Int): PagedResult<AnimeSummary> =
        page(Queries.BY_GENRE, mapOf("genre" to genre, "page" to page.toString()))

    override suspend fun byStudio(studioId: String, page: Int): PagedResult<AnimeSummary> =
        page(Queries.BY_STUDIO, mapOf("q" to studioId, "page" to page.toString()))

    override suspend fun detail(animeId: String): AnimeDetail = withBackoff {
        val response: GraphQlResponse<DetailData> = post(
            Queries.DETAIL,
            mapOf("id" to animeId),
        )
        val media = response.data?.Media ?: throw SourceException("Anime $animeId not found")
        AnimeDetail(
            summary = media.toSummary(),
            synopsis = media.description?.replace(HTML_TAG, "")?.trim(),
            bannerUrl = media.bannerImage,
            genres = media.genres,
            studios = media.studios?.edges?.map { it.node.name } ?: emptyList(),
            seasonYear = media.seasonYear,
            airingStatus = media.status,
            totalEpisodes = media.episodes,
            trailerUrl = null,
        )
    }

    override suspend fun episodes(animeId: String): List<Episode> {
        val total = detail(animeId).totalEpisodes ?: return emptyList()
        return (1..total).map { number ->
            Episode(
                animeId = animeId,
                sourceId = id,
                number = number,
                title = "Episode $number",
            )
        }
    }

    override suspend fun streams(episode: Episode): List<StreamLink> = emptyList()

    private suspend fun page(query: String, vars: Map<String, String>): PagedResult<AnimeSummary> =
        withBackoff {
            val response: GraphQlResponse<PageData> = post(query, vars)
            val page = response.data?.Page ?: throw SourceException("Empty catalogue response")
            PagedResult(
                items = page.media.map { it.toSummary() },
                page = page.pageInfo.currentPage ?: vars["page"]?.toIntOrNull() ?: 1,
                hasNextPage = page.pageInfo.hasNextPage ?: false,
            )
        }

    private suspend fun <T> post(query: String, vars: Map<String, String>): GraphQlResponse<T> {
        val body = json.encodeToString(
            GraphQlRequest.serializer(),
            GraphQlRequest(query.trimIndent(), vars),
        ).toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Accept", "application/json")
            .build()

        val cacheKey = query.hashCode().toString() + vars.toString()
        val cached = synchronized(cache) { cache[cacheKey] }
        val now = System.currentTimeMillis()
        val payload = if (cached != null && now - cached.at < CACHE_TTL_MS) {
            cached.payload
        } else {
            val fresh = execute(request)
            synchronized(cache) { cache[cacheKey] = CachedEntry(fresh, now) }
            fresh
        }

        @Suppress("UNCHECKED_CAST")
        val parsed = when {
            // GraphQlResponse<T> cannot be reified, so pick the concrete shape by inspecting the query:
            // only DETAIL returns a single Media object, everything else returns a Page.
            query.contains("Page(") -> json.decodeFromString(pageAdapter, payload)
            else -> json.decodeFromString(detailAdapter, payload)
        }
        return parsed as GraphQlResponse<T>
    }

    private fun execute(request: Request): String {
        val response = try {
            http.newCall(request).execute()
        } catch (io: IOException) {
            throw SourceException(
                "Network error talking to AniList (${io.javaClass.simpleName}: ${io.message})",
                io,
                SourceException.Reason.NETWORK,
            )
        }
        response.use {
            val text = it.body?.string().orEmpty()
            return when {
                it.code == 429 -> throw SourceException(
                    "AniList rate limit reached, try again shortly",
                    reason = SourceException.Reason.RATE_LIMITED,
                )
                it.code == 404 -> throw SourceException(
                    "AniList has no record for that id",
                    reason = SourceException.Reason.NOT_FOUND,
                )
                !it.isSuccessful -> throw SourceException(
                    "AniList responded ${it.code}",
                    reason = SourceException.Reason.PAYLOAD,
                )
                text.isBlank() -> throw SourceException(
                    "AniList returned an empty body",
                    reason = SourceException.Reason.PAYLOAD,
                )
                else -> text
            }
        }
    }

    private suspend fun <T> withBackoff(block: suspend () -> T): T {
        var attempt = 0
        var lastRateLimit: SourceException? = null
        while (true) {
            try {
                return block()
            } catch (e: SourceException) {
                // Only transport and rate-limit failures are worth another try; a 404 or a bad
                // payload will fail identically and just delays the error screen.
                val retryable = e.reason == SourceException.Reason.NETWORK ||
                    e.reason == SourceException.Reason.RATE_LIMITED
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    // The last attempt is reported first-hand: a retried 429 that timed out on the
                    // second try should still tell the user what actually went wrong upstream.
                    throw lastRateLimit ?: e
                }
                if (e.reason == SourceException.Reason.RATE_LIMITED) {
                    lastRateLimit = e
                    // 429s do not need a backoff delay here: AniList throttles per window, and the
                    // caller's error screen is more useful than half a second of hope.
                    continue
                }
                kotlinx.coroutines.delay(500L * (1 shl attempt))
            }
            attempt++
        }
    }

    private fun Media.toSummary() = AnimeSummary(
        id = id.toString(),
        sourceId = SOURCE_ID,
        title = title.romaji ?: title.english ?: title.native ?: "Untitled",
        secondaryTitle = title.english?.takeIf { it != title.romaji },
        coverUrl = coverImage?.extraLarge ?: coverImage?.large,
        format = format,
        episodes = episodes,
        status = status,
        score = averageScore?.let { it / 10.0 },
        year = seasonYear,
        isAdult = isAdult ?: false,
    )

    companion object {
        const val SOURCE_ID = "anilist"
        private const val MAX_ATTEMPTS = 2
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private val HTML_TAG = Regex("<[^>]+>")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
