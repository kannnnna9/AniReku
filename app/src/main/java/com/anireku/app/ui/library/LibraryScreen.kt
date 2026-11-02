package com.anireku.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anireku.app.core.model.AnimeSummary
import com.anireku.app.ui.common.PosterCard
import com.anireku.app.ui.common.StateBox

@Composable
fun LibraryScreen(
    onOpenAnime: (String, String) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saved (${bookmarks.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${history.size})") })
        }

        when (tab) {
            0 -> if (bookmarks.isEmpty()) {
                StateBox(
                    title = "Nothing saved yet",
                    message = "Tap Save on an anime to keep it here.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(bookmarks, key = { it.animeId }) { b ->
                        PosterCard(
                            anime = AnimeSummary(
                                id = b.animeId,
                                sourceId = b.sourceId,
                                title = b.title,
                                coverUrl = b.cover,
                            ),
                            onClick = { onOpenAnime(b.animeId, b.sourceId) },
                        )
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.animeId + it.episodeNumber }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenAnime(item.animeId, item.sourceId) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(width = 48.dp, height = 68.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            if (item.cover != null) {
                                AsyncImage(
                                    model = item.cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Episode ${item.episodeNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
