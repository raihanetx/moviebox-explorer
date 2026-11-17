package com.example.movieboxexplorer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.coroutines.delay

/**
 * The home page.
 *
 * The real MovieBox home: a hero carousel over the banner slides, the platform strip,
 * quick-browse shortcuts and every content rail the website lists — in its order,
 * with its rows. The presentation is a modern Material 3 take on it: rounded artwork,
 * a gradient hero with a title/meta/CTA overlay, soft ambient colour, skeletons and
 * press feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: (SubjectBrief) -> Unit,
    onSearch: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowse: (String) -> Unit = {},
    onDownload: (SubjectBrief) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HomeTopBar(
                onSearch = onSearch,
                onOpenRanking = onOpenRanking,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // A soft brand glow behind the hero, the way modern streaming apps do it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            if (state.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                )
            }

            when {
                state.loading -> HomeSkeleton()

                state.error != null && state.banners.isEmpty() && state.rails.isEmpty() -> ErrorBox(
                    message = state.error ?: "Something went wrong",
                    onRetry = viewModel::refresh,
                )

                state.isEmpty -> EmptyHome(onRetry = viewModel::refresh)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item(key = "search") {
                        HomeSearchBar(onClick = onSearch)
                    }

                    if (state.banners.isNotEmpty()) {
                        item(key = "hero") {
                            HeroCarousel(banners = state.banners, onOpen = onOpen)
                        }
                    }

                    if (state.platforms.isNotEmpty()) {
                        item(key = "platforms") {
                            PlatformStrip(platforms = state.platforms)
                        }
                    }

                    item(key = "browse") {
                        QuickBrowse(onBrowse = onBrowse)
                    }

                    items(state.rails) { rail ->
                        HomeRailSection(
                            rail = rail,
                            onOpen = onOpen,
                            onSeeAll = { onBrowse("movies") },
                            onDownload = onDownload,
                        )
                    }

                    item(key = "footer") { FooterNote() }
                }
            }
        }
    }
}

