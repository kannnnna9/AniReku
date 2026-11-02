package com.anireku.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anireku.app.ui.common.AnimeGrid
import com.anireku.app.ui.common.StateBox

@Composable
fun SearchScreen(
    onOpenAnime: (String, String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Search anime…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> StateBox(
                title = "Search failed",
                message = state.error,
                onRetry = vm::submit,
            )

            state.searched && state.results.isEmpty() -> StateBox(
                title = "Nothing found",
            )

            !state.searched -> StateBox(
                title = "Find something to watch",
                message = "Type a title; results come from the metadata source you have enabled.",
            )

            else -> AnimeGrid(
                items = state.results,
                onOpen = { onOpenAnime(it.id, it.sourceId) },
            )
        }
    }
}
