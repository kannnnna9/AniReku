package com.anireku.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anireku.app.core.model.Episode
import com.anireku.app.ui.common.StateBox

@Composable
fun DetailScreen(
    animeId: String,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit = {},
    vm: DetailViewModel = viewModel(),
) {
    LaunchedEffect(animeId) { vm.loadOnce() }
    val state by vm.state.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarked.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.error != null -> StateBox(
            title = "Could not open this anime",
            message = state.error,
            onRetry = vm::load,
        )

        state.detail != null -> {
            val detail = state.detail!!
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (detail.bannerUrl != null) {
                        AsyncImage(
                            model = detail.bannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        MaterialTheme.colorScheme.background,
                                    )
                                )
                            ),
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 0.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 136.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (detail.summary.coverUrl != null) {
                            AsyncImage(
                                model = detail.summary.coverUrl,
                                contentDescription = detail.summary.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            detail.summary.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            detail.summary.format,
                            detail.seasonYear?.toString(),
                            detail.summary.episodes?.let { "$it episodes" },
                            detail.summary.score?.let { "★ %.1f".format(it) },
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                meta.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Row(
                            Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AssistChip(
                                onClick = { vm.toggleBookmark() },
                                label = { Text(if (bookmarked) "Saved" else "Save") },
                                leadingIcon = {
                                    Icon(
                                        if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = null,
                                    )
                                },
                            )
                            if (detail.airingStatus != null) {
                                AssistChip(onClick = {}, label = { Text(detail.airingStatus) })
                            }
                        }
                    }
                }

                if (!detail.synopsis.isNullOrBlank()) {
                    Text(
                        "Synopsis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                    )
                    Text(
                        detail.synopsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (detail.genres.isNotEmpty()) {
                    Text(
                        detail.genres.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                Text(
                    "Episodes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                )
                if (state.feedMessage != null) {
                    Text(
                        state.feedMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                EpisodeGrid(
                    episodes = state.episodes,
                    playable = state.playable,
                    onPlay = onPlayEpisode,
                )
                Box(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    playable: Boolean,
    onPlay: (Episode) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(((episodes.size / 2 + 1) * 56).dp.coerceAtMost(560.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = true,
    ) {
        items(episodes, key = { it.number }) { ep ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (playable) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (playable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        "Episode %02d".format(ep.number),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (ep.availableQualities.isNotEmpty()) {
                        Text(
                            ep.availableQualities.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
