package com.anireku.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anireku.app.Graph
import com.anireku.app.core.database.LibraryItem
import com.anireku.app.core.model.AnimeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val trending: List<AnimeSummary> = emptyList(),
    val seasonal: List<AnimeSummary> = emptyList(),
    val seasonLabel: String = "",
)

class HomeViewModel : ViewModel() {

    private val repo = Graph.repository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val continueWatching: StateFlow<List<LibraryItem>> =
        repo.continueWatching().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val trending = repo.trending()
                val seasonal = repo.seasonal()
                _state.value = HomeUiState(
                    loading = false,
                    trending = trending.items,
                    seasonal = seasonal.items,
                    seasonLabel = currentSeasonLabel(),
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not reach the catalogue.",
                )
            }
        }
    }

    private fun currentSeasonLabel(): String {
        val now = java.time.LocalDate.now()
        val name = when (now.monthValue) {
            in 1..3 -> "Winter"
            in 4..6 -> "Spring"
            in 7..9 -> "Summer"
            else -> "Fall"
        }
        return "$name ${now.year}"
    }
}
