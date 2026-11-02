package com.anireku.app

import android.app.Application
import androidx.room.Room
import com.anireku.app.core.database.AppDatabase
import com.anireku.app.core.repository.ContentRepository
import com.anireku.app.source.anilist.AniListSource
import com.anireku.app.source.feed.LocalFeedSource
import java.io.File

/**
 * Manual dependency graph. Small enough that a container library would be ceremony; when the graph
 * outgrows this file (more sources, download manager, updater) it moves to Hilt as a single patch.
 */
object Graph {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ContentRepository
        private set

    lateinit var feedSource: LocalFeedSource
        private set

    fun init(app: Application) {
        if (::database.isInitialized) return

        database = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

        feedSource = LocalFeedSource(
            fileProvider = { File(File(app.getExternalFilesDir(null), ""), "sources.json") },
        )

        repository = ContentRepository(
            database = database,
            metadata = AniListSource(),
            feeds = feedSource,
        )
    }
}
