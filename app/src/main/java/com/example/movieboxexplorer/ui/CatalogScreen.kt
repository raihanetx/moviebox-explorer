package com.example.movieboxexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movieboxexplorer.data.CatalogContent
import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.FilterGroup
import com.example.movieboxexplorer.data.SubjectBrief

/**
 * A catalog screen, copied from the matching page of the website.
 *
 * The website renders the movie catalog as a stack of rows and the TV and animation
 * catalogs as a flat poster grid, so the screen does whichever the page uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    title: String,
    onOpen: (SubjectBrief) -> Unit,
    onDownload: (SubjectBrief) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        when (val current = state) {
            is CatalogUiState.Loading -> LoadingBox("Loading $title…")

            is CatalogUiState.Error -> ErrorBox(current.message, onRetry = viewModel::load)

            is CatalogUiState.Ready -> CatalogContentView(
                content = current.content,
                filters = current.filters,
                filter = current.filter,
                loadingMore = current.loadingMore,
                filtering = current.filtering,
                onSelectFilter = viewModel::selectFilter,
                onLoadMore = viewModel::loadMore,
                onOpen = onOpen,
                onDownload = onDownload,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

/**
 * Renders a catalog exactly as the matching page of the website does.
 *
 * Shared by the full-screen catalog screens and the home page's tabs so the two can
 * never drift apart: same rows, same grid, same filter bar, same load-more behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogContentView(
    content: CatalogContent,
    filters: List<FilterGroup>,
    filter: CatalogFilter,
    loadingMore: Boolean,
    onSelectFilter: (String, String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (SubjectBrief) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    onDownload: ((SubjectBrief) -> Unit)? = null,
    filtering: Boolean = false,
) {
    when (content) {
        is CatalogContent.Rows -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(
                content.rows,
                key = { index, row -> "${index}_${row.title}" },
            ) { _, row ->
                CatalogRowView(title = row.title, items = row.items, onClick = onOpen)
            }
        }

        is CatalogContent.Grid -> Column(modifier = modifier) {
            if (filters.isNotEmpty()) {
                FilterBar(
                    filters = filters,
                    selected = filter,
                    onSelect = onSelectFilter,
                )
            }
            if (filtering) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
            PosterGrid(
                items = content.grid.items,
                hasMore = content.grid.hasMore,
                loadingMore = loadingMore,
                onLoadMore = onLoadMore,
                onClick = onOpen,
                modifier = Modifier.fillMaxSize(),
                columns = columns,
                onDownload = onDownload,
            )
        }
    }
}

/**
 * The website's filter bar — one group per facet, from the page's own payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBar(
    filters: List<FilterGroup>,
    selected: CatalogFilter,
    onSelect: (String, String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(filters, key = { it.filterType }) { group ->
            var expanded by remember { mutableStateOf(false) }
            val currentId = valueFor(group.filterType, selected)
            val currentName = group.filterVals.firstOrNull { it.id == currentId }?.name
            val label = (currentName ?: group.title).ifBlank { "All" }

            Box {
                FilterChip(
                    selected = currentId != null,
                    onClick = { expanded = true },
                    label = { Text(label) },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All ${group.title.lowercase()}") },
                        onClick = {
                            onSelect(group.filterType, null)
                            expanded = false
                        },
                    )
                    group.filterVals.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                onSelect(group.filterType, option.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun valueFor(type: String, filter: CatalogFilter): String? = when (type) {
    "genre" -> filter.genre
    "country" -> filter.country
    "year" -> filter.year
    "sort" -> filter.sort
    "rate" -> filter.rate
    "classify" -> filter.classify
    else -> null
}
