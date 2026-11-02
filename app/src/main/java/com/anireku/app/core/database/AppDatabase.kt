package com.anireku.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val secondaryTitle: String?,
    val titleNative: String?,
    val cover: String?,
    val banner: String?,
    val synopsis: String?,
    val genres: String,
    val studios: String,
    val year: Int?,
    val status: String?,
    val episodesTotal: Int?,
    val score: Double?,
    val updatedAt: Long,
)

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val animeId: String,
    val sourceId: String,
    val title: String,
    val cover: String?,
    val addedAt: Long,
)

@Entity(tableName = "history", primaryKeys = ["animeId", "episodeNumber"])
data class HistoryEntity(
    val animeId: String,
    val sourceId: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val animeTitle: String,
    val cover: String?,
    val watchedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)

@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey val key: String,
    val animeId: String,
    val sourceId: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val url: String,
    val quality: String,
    val state: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val path: String?,
    val createdAt: Long,
)

data class LibraryItem(
    val animeId: String,
    val sourceId: String,
    val title: String,
    val cover: String?,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
)

@Dao
interface AnimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnimeEntity)

    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun find(id: String): AnimeEntity?

    @Query("DELETE FROM anime WHERE updatedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE animeId = :animeId")
    suspend fun removeBookmark(animeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE animeId = :animeId)")
    fun isBookmarked(animeId: String): Flow<Boolean>

    @Query("SELECT * FROM bookmark ORDER BY addedAt DESC")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(history: HistoryEntity)

    @Query(
        """
        SELECT animeId, sourceId, animeTitle AS title, cover, episodeNumber, positionMs, durationMs, watchedAt
        FROM history ORDER BY watchedAt DESC
        """
    )
    fun history(): Flow<List<LibraryItem>>

    @Query(
        """
        SELECT animeId, sourceId, animeTitle AS title, cover, episodeNumber, positionMs, durationMs, watchedAt
        FROM history GROUP BY animeId ORDER BY watchedAt DESC LIMIT :limit
        """
    )
    fun continueWatching(limit: Int = 12): Flow<List<LibraryItem>>

    @Query("DELETE FROM history WHERE animeId = :animeId")
    suspend fun clearHistory(animeId: String)

    @Query("SELECT * FROM history WHERE animeId = :animeId AND episodeNumber = :number")
    suspend fun progress(animeId: String, number: Int): HistoryEntity?
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("SELECT * FROM download ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE key = :key")
    suspend fun find(key: String): DownloadEntity?

    @Query("DELETE FROM download WHERE key = :key")
    suspend fun delete(key: String)
}

@Database(
    entities = [AnimeEntity::class, BookmarkEntity::class, HistoryEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun libraryDao(): LibraryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "anireku.db"
    }
}
