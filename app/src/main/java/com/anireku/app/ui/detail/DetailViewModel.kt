package com.anireku.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anireku.app.Graph
import com.anireku.app.core.database.HistoryEntity
import com.anireku.app.core.model.AnimeDetail
import com.anireku.app.core.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: AnimeDetail? = null,
    val episodes: List<Episode> = emptyList(),
    val playable: Boolean = false,
    val progressByEpisode: Map<Int, HistoryEntity> = emptyMap(),
    val feedMessage: String? = null,
)

class DetailViewModel(savedState: SavedStateHandle) : ViewModel() {

    private val animeId: String = savedState.get<String>("id").orEmpty()
    private val repo = Graph.repository

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val bookmarked: StateFlow<Boolean> =
        repo.isBookmarked(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val detail = repo.detail(animeId)
                val episodes = repo.episodes(detail)
                val playable = repo.hasStreams(animeId)
                _state.value = DetailUiState(
                    loading = false,
                    detail = detail,
                    episodes = episodes,
                    playable = playable,
                    feedMessage = if (playable) null else
                        "No streams configured for this anime. Add it to your library file to play it.",
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not load this anime.",
                )
            }
        }
    }

    fun toggleBookmark() {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            // Bookmark state is mirrored in a flow, so a single upsert/remove is enough.
            if (bookmarked.value) repo.removeBookmark(detail.summary.id) else repo.toggleBookmark(detail)
        }
    }
}
