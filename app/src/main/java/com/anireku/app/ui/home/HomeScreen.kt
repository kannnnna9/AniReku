package com.anireku.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anireku.app.core.database.LibraryItem
import com.anireku.app.core.model.AnimeSummary
import com.anireku.app.ui.common.PosterCard
import com.anireku.app.ui.common.StateBox

@Composable
fun HomeScreen(
    onOpenAnime: (String, String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val resume by vm.continueWatching.collectAsStateWithLifecycle()

    when {
        state.loading && state.trending.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.error != null && state.trending.isEmpty() -> StateBox(
            title = "Could not load the catalogue",
            message = state.error,
            onRetry = vm::load,
        )

        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                "AniReku",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (resume.isNotEmpty()) {
                SectionTitle("Continue watching")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(resume, key = { it.animeId }) { item ->
                        ResumeCard(item) { onOpenAnime(item.animeId, item.sourceId) }
                    }
                }
            }

            if (state.trending.isNotEmpty()) {
                SectionTitle("Trending now")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.trending, key = { it.id }) { anime ->
                        Box(Modifier.width(112.dp)) {
                            PosterCard(anime) { onOpenAnime(anime.id, anime.sourceId) }
                        }
                    }
                }
            }

            if (state.seasonal.isNotEmpty()) {
                SectionTitle(state.seasonLabel.ifBlank { "This season" })
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.seasonal, key = { it.id }) { anime ->
                        Box(Modifier.width(112.dp)) {
                            PosterCard(anime) { onOpenAnime(anime.id, anime.sourceId) }
                        }
                    }
                }
            }

            if (state.trending.isEmpty() && state.error == null) {
                StateBox(
                    title = "Nothing to show yet",
                    message = "The catalogue came back empty. Check your connection and pull again.",
                    onRetry = vm::load,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ResumeCard(item: LibraryItem, onClick: () -> Unit) {
    Row(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.cover != null) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Episode ${item.episodeNumber} · ${formatPosition(item.positionMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val fraction = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                } else 0f
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun formatPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
