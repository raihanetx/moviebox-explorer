package com.example.movieboxexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.data.CatalogRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader

@Composable
fun CatalogRowView(
    title: String,
    items: List<SubjectBrief>,
    onClick: (SubjectBrief) -> Unit,
) {
    if (items.isEmpty()) return
    SectionTitle(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { index, it -> "${index}_${it.subjectId}" }) { _, item ->
            PosterCard(item = item, onClick = { onClick(item) })
        }
    }
}

/** Full-width vertical list of rows. */
@Composable
fun CatalogRows(
    rows: List<CatalogRow>,
    onClick: (SubjectBrief) -> Unit,
) {
    rows.forEach { row ->
        CatalogRowView(title = row.title, items = row.items, onClick = onClick)
    }
}

/**
 * Flat poster grid — how the website renders its TV and animation catalogs.
 *
 * "Load more" asks the site for the next page of the same route, exactly as the
 * website does when the grid runs out.
 */
@Composable
fun PosterGrid(
    items: List<SubjectBrief>,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onClick: (SubjectBrief) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    onDownload: ((SubjectBrief) -> Unit)? = null,
) {
    val listState = rememberLazyGridState()
    val context = LocalContext.current
    val prefetcher = remember { ImageLoader(context) }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= layout.totalItemsCount - 4
        }
    }

    // Warm the image cache for every poster the grid is about to show — the
    // network fetch of covers is the dominant cost after the JSON payload.
    LaunchedEffect(items) {
        items.forEach { item ->
            item.cover?.url?.let { url ->
                prefetcher.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .build(),
                )
            }
        }
    }

    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items, key = { index, it -> "${index}_${it.subjectId}" }) { _, item ->
            GridPosterCard(
                item = item,
                onClick = { onClick(item) },
                onDownload = onDownload,
            )
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

/** One poster in the grid — artwork with the title overlaid, like a streaming app. */
@Composable
internal fun GridPosterCard(
    item: SubjectBrief,
    onClick: () -> Unit,
    onDownload: ((SubjectBrief) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.cover?.url,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.releaseDate.take(4).isNotBlank()) {
                            Text(
                                text = item.releaseDate.take(4),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                        if (item.imdbRatingValue.isNotBlank()) {
                            Text(
                                text = if (item.releaseDate.take(4).isNotBlank()) "  •  ★ ${item.imdbRatingValue}"
                                else "★ ${item.imdbRatingValue}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC857),
                            )
                        }
                    }
                }
            }
            if (item.webHighRisk) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC8E24AA))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "UNLISTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TypePill(
                item = item,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            onDownload?.let { download ->
                DownloadPill(
                    item = item,
                    onDownload = download,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

