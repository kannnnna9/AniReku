package com.anireku.app.source.feed

import com.anireku.app.core.model.AnimeDetail
import com.anireku.app.core.model.AnimeSummary
import com.anireku.app.core.model.Capability
import com.anireku.app.core.model.Episode
import com.anireku.app.core.model.PagedResult
import com.anireku.app.core.model.Season
import com.anireku.app.core.model.SourceException
import com.anireku.app.core.model.StreamLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Streams the user supplies: a JSON file in Download/anireku/sources.json, or a URL to such a file.
 *
 * The app never fabricates a URL. If the file lists an episode, that episode plays; if it does not,
 * the app says so. This keeps the app a player, not a source of content.
 */
class LocalFeedSource(
    private val fileProvider: () -> File?,
    private val remoteUrlProvider: () -> String? = { null },
    private val http: OkHttpClient = OkHttpClient(),
) : com.anireku.app.core.model.AnimeSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "My library"

    // The feed knows streams but not the catalogue: searching is delegated to the metadata source.
    override val capabilities: Set<Capability> = setOf(Capability.STREAMS, Capability.EPISODES)

    @Volatile
    private var cached: StreamFeed? = null
    @Volatile
    private var cachedAt: Long = 0

    private suspend fun feed(force: Boolean = false): StreamFeed? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hit = cached
        if (!force && hit != null && now - cachedAt < TTL_MS) return@withContext hit

        val raw = loadRaw()
        if (raw == null) {
            if (hit != null) return@withContext hit
            throw SourceException(
                "No library file found. Put sources.json in Download/anireku/ or set a feed URL in Settings."
            )
        }
        val parsed = FeedParser.parse(raw).getOrElse { throw SourceException(it.message ?: "Invalid feed") }
        cached = parsed
        cachedAt = now
        parsed
    }

    private fun loadRaw(): String? {
        remoteUrlProvider()?.takeIf { it.isNotBlank() }?.let { url ->
            val request = Request.Builder().url(url).build()
            runCatching {
                http.newCall(request).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                }
            }.getOrNull()?.let { return it }
        }
        val file = fileProvider() ?: return null
        return if (file.exists() && file.canRead()) file.readText() else null
    }

    suspend fun entryFor(animeId: String): FeedEntry? = feed()?.entries?.firstOrNull {
        it.anilistId.toString() == animeId
    }

    override suspend fun episodes(animeId: String): List<Episode> {
        val entry = entryFor(animeId) ?: return emptyList()
        return entry.episodes.sortedBy { it.number }.map {
            FeedParser.toEpisode(animeId, SOURCE_ID, it)
        }
    }

    override suspend fun streams(episode: Episode): List<StreamLink> {
        val entry = entryFor(episode.animeId) ?: return emptyList()
        val ep = entry.episodes.firstOrNull { it.number == episode.number } ?: return emptyList()
        return FeedParser.toStreamLinks(ep)
    }

    /** Catalogue calls are not this source's job. The repository routes them to a metadata source. */
    override suspend fun search(query: String, page: Int): PagedResult<AnimeSummary> = emptyPage(page)
    override suspend fun trending(page: Int): PagedResult<AnimeSummary> = emptyPage(page)
    override suspend fun seasonal(season: Season, year: Int, page: Int): PagedResult<AnimeSummary> =
        emptyPage(page)
    override suspend fun byGenre(genre: String, page: Int): PagedResult<AnimeSummary> = emptyPage(page)
    override suspend fun byStudio(studioId: String, page: Int): PagedResult<AnimeSummary> = emptyPage(page)

    override suspend fun detail(animeId: String): AnimeDetail = throw SourceException(
        "The library file does not describe this anime; open it through a metadata source."
    )

    private fun emptyPage(page: Int) = PagedResult<AnimeSummary>(emptyList(), page, false)

    /** Every anime id that has episodes in the feed — used to mark entries in the catalogue. */
    suspend fun availableIds(): Set<String> = feed()?.entries?.map { it.anilistId.toString() }?.toSet()
        ?: emptySet()

    companion object {
        const val SOURCE_ID = "userfeed"
        private const val TTL_MS = 60_000L
    }
}
