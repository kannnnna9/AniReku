package com.anireku.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anireku.app.core.model.AnimeSummary

@Composable
fun AnimeGrid(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    loadingMore: Boolean = false,
) {
    val arrangement = Arrangement.spacedBy(10.dp)
    val vertical = Arrangement.spacedBy(14.dp)
    val cells = GridCells.Adaptive(112.dp)

    if (gridState != null) {
        LazyVerticalGrid(
            columns = cells,
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = arrangement,
            verticalArrangement = vertical,
            modifier = modifier.fillMaxSize(),
        ) {
            animeGridBody(items, onOpen, loadingMore)
        }
    } else {
        LazyVerticalGrid(
            columns = cells,
            contentPadding = contentPadding,
            horizontalArrangement = arrangement,
            verticalArrangement = vertical,
            modifier = modifier.fillMaxSize(),
        ) {
            animeGridBody(items, onOpen, loadingMore)
        }
    }
}

/** Body of the poster grid; shared by both state variants so the item definition exists once. */
private fun LazyGridScope.animeGridBody(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    loadingMore: Boolean,
) {
    items.forEach { anime ->
        item(key = anime.sourceId + anime.id) {
            PosterCard(anime = anime, onClick = { onOpen(anime) })
        }
    }
    if (loadingMore) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PosterCard(anime: AnimeSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                ),
        ) {
            if (anime.coverUrl != null) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (anime.score != null) {
                Text(
                    text = "★ %.1f".format(anime.score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        val meta = listOfNotNull(anime.format, anime.episodes?.let { "$it eps" }, anime.year?.toString())
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
fun StateBox(
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text("Try again")
            }
        }
    }
}
