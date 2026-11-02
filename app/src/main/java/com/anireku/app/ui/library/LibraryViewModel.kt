package com.anireku.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anireku.app.Graph
import com.anireku.app.core.database.BookmarkEntity
import com.anireku.app.core.database.LibraryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repo = Graph.repository

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repo.bookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<LibraryItem>> =
        repo.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeBookmark(animeId: String) {
        viewModelScope.launch { repo.removeBookmark(animeId) }
    }
}
