package com.anireku.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.anireku.app.ui.theme.AniRekuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Full-screen player. The episode is opened with a resolved stream URL; the activity is responsible
 * for transport only (no source lookup here, so a source that hands out a rotating link stays in control).
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val animeId = intent.getStringExtra(EXTRA_ANIME_ID).orEmpty()
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE, 1)
        val startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)

        if (url.isBlank()) {
            finish()
            return
        }

        val mediaFactory = DefaultMediaSourceFactory(
            OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                .setUserAgent(USER_AGENT)
                .setDefaultRequestProperties(HEADERS)
        )

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaFactory)
            .build()
        player = exo

        val hls = url.contains(".m3u8", ignoreCase = true)
        if (hls) {
            exo.setMediaItem(MediaItem.fromUri(url), startPosition)
        } else {
            exo.setMediaItem(MediaItem.fromUri(url), startPosition)
        }
        exo.prepare()
        exo.playWhenReady = true

        setContent {
            AniRekuTheme {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exo
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Persist progress every five seconds; the library screen reads it back.
        progressJob = scope.launch {
            while (true) {
                delay(5_000)
                val p = player ?: break
                if (p.isPlaying) {
                    val ms = p.currentPosition
                    val dur = p.duration.coerceAtLeast(0)
                    if (dur > 0 && ms > 15_000) {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            runCatching {
                                Graph.database.libraryDao().saveProgress(
                                    com.anireku.app.core.database.HistoryEntity(
                                        animeId = animeId,
                                        sourceId = "anilist",
                                        episodeNumber = episodeNumber,
                                        episodeTitle = "Episode $episodeNumber",
                                        animeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                                        cover = null,
                                        watchedAt = System.currentTimeMillis(),
                                        positionMs = ms,
                                        durationMs = dur,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_ANIME_ID = "animeId"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_POSITION = "position"
        const val EXTRA_TITLE = "title"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

        // Some hosts refuse a request without a Referer; empty by default and overridden per link later.
        private val HEADERS: Map<String, String> = emptyMap()

        fun intent(
            ctx: android.content.Context,
            url: String,
            animeId: String,
            episodeNumber: Int,
            title: String,
            positionMs: Long = 0L,
        ): Intent = Intent(ctx, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_ANIME_ID, animeId)
            putExtra(EXTRA_EPISODE, episodeNumber)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_POSITION, positionMs)
        }
    }
}
