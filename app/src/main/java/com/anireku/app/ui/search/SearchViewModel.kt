package com.anireku.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anireku.app.Graph
import com.anireku.app.core.model.AnimeSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<AnimeSummary> = emptyList(),
    val searched: Boolean = false,
)

class SearchViewModel : ViewModel() {

    private val repo = Graph.repository
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var pending: Job? = null

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
        pending?.cancel()
        if (text.isBlank()) {
            _state.value = SearchUiState(query = text)
            return
        }
        pending = viewModelScope.launch {
            delay(400) // debounce: a keystroke storm on mobile data is wasteful
            runSearch(text)
        }
    }

    fun submit() {
        pending?.cancel()
        viewModelScope.launch { runSearch(_state.value.query) }
    }

    private suspend fun runSearch(text: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { repo.search(text) }
            .onSuccess { page ->
                _state.value = _state.value.copy(
                    loading = false,
                    results = page.items,
                    searched = true,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed.",
                    searched = true,
                )
            }
    }
}
