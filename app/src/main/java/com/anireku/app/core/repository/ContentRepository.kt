package com.anireku.app.core.repository

import com.anireku.app.core.database.AnimeEntity
import com.anireku.app.core.database.AppDatabase
import com.anireku.app.core.database.BookmarkEntity
import com.anireku.app.core.database.HistoryEntity
import com.anireku.app.core.model.AnimeDetail
import com.anireku.app.core.model.AnimeSource
import com.anireku.app.core.model.AnimeSummary
import com.anireku.app.core.model.Episode
import com.anireku.app.core.model.PagedResult
import com.anireku.app.core.model.SourceException
import com.anireku.app.core.model.StreamLink
import com.anireku.app.source.anilist.AniListSource
import com.anireku.app.source.feed.LocalFeedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single entry point for content. The UI never touches a source directly, so swapping or adding a
 * source is a one-line change here.
 */
class ContentRepository(
    private val database: AppDatabase,
    private val metadata: AniListSource,
    private val feeds: LocalFeedSource,
) {
    val source: AnimeSource = metadata

    suspend fun trending(page: Int = 1): PagedResult<AnimeSummary> = metadata.trending(page)

    suspend fun seasonal(page: Int = 1): PagedResult<AnimeSummary> {
        val now = java.time.LocalDate.now()
        val season = seasonFor(now.monthValue)
        return metadata.seasonal(season, now.year, page)
    }

    suspend fun search(query: String, page: Int = 1): PagedResult<AnimeSummary> {
        if (query.isBlank()) return PagedResult(emptyList(), page, false)
        return metadata.search(query.trim(), page)
    }

    suspend fun byGenre(genre: String, page: Int = 1): PagedResult<AnimeSummary> =
        metadata.byGenre(genre, page)

    suspend fun detail(animeId: String): AnimeDetail {
        val detail = metadata.detail(animeId)
        database.animeDao().upsert(detail.toEntity())
        return detail
    }

    /** Episodes from the user's library, falling back to generated episode placeholders. */
    suspend fun episodes(detail: AnimeDetail): List<Episode> {
        val fromFeed = runCatching { feeds.episodes(detail.summary.id) }.getOrDefault(emptyList())
        return if (fromFeed.isNotEmpty()) fromFeed else metadata.episodes(detail.summary.id)
    }

    suspend fun streams(episode: Episode): List<StreamLink> =
        runCatching { feeds.streams(episode) }.getOrDefault(emptyList())

    /** True when at least one stream URL exists for this anime — drives the "playable" badge. */
    suspend fun hasStreams(animeId: String): Boolean =
        runCatching { feeds.episodes(animeId).isNotEmpty() }.getOrDefault(false)

    fun bookmarks(): Flow<List<BookmarkEntity>> = database.libraryDao().bookmarks()

    fun isBookmarked(animeId: String): Flow<Boolean> = database.libraryDao().isBookmarked(animeId)

    suspend fun toggleBookmark(detail: AnimeDetail) {
        val dao = database.libraryDao()
        dao.addBookmark(
            BookmarkEntity(
                animeId = detail.summary.id,
                sourceId = detail.summary.sourceId,
                title = detail.summary.title,
                cover = detail.summary.coverUrl,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeBookmark(animeId: String) = database.libraryDao().removeBookmark(animeId)

    suspend fun saveProgress(detail: AnimeDetail, episode: Episode, positionMs: Long, durationMs: Long) {
        database.libraryDao().saveProgress(
            HistoryEntity(
                animeId = detail.summary.id,
                sourceId = detail.summary.sourceId,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
                animeTitle = detail.summary.title,
                cover = detail.summary.coverUrl,
                watchedAt = System.currentTimeMillis(),
                positionMs = positionMs,
                durationMs = durationMs,
            )
        )
    }

    suspend fun progress(animeId: String, number: Int): HistoryEntity? =
        database.libraryDao().progress(animeId, number)

    fun continueWatching() = database.libraryDao().continueWatching()

    fun history() = database.libraryDao().history()

    fun libraryTitles() = bookmarks().map { list -> list.map { it.title } }

    private fun seasonFor(month: Int): com.anireku.app.core.model.Season = when (month) {
        in 1..3 -> com.anireku.app.core.model.Season("WINTER", 0)
        in 4..6 -> com.anireku.app.core.model.Season("SPRING", 0)
        in 7..9 -> com.anireku.app.core.model.Season("SUMMER", 0)
        else -> com.anireku.app.core.model.Season("FALL", 0)
    }

    private fun AnimeDetail.toEntity() = AnimeEntity(
        id = summary.id,
        source = summary.sourceId,
        title = summary.title,
        secondaryTitle = summary.secondaryTitle,
        titleNative = null,
        cover = summary.coverUrl,
        banner = bannerUrl,
        synopsis = synopsis,
        genres = genres.joinToString(","),
        studios = studios.joinToString(","),
        year = seasonYear,
        status = airingStatus,
        episodesTotal = totalEpisodes,
        score = summary.score,
        updatedAt = System.currentTimeMillis(),
    )
}
