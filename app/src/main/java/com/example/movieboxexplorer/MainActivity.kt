package com.example.movieboxexplorer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.Coil
import coil.ImageLoader
import com.example.movieboxexplorer.data.AppSettings
import com.example.movieboxexplorer.data.Downloads
import com.example.movieboxexplorer.data.HomeCache
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.ui.CatalogKind
import com.example.movieboxexplorer.ui.AdultGateDialog
import com.example.movieboxexplorer.ui.DEMO_VIDEO
import com.example.movieboxexplorer.ui.CatalogScreen
import com.example.movieboxexplorer.ui.CatalogViewModel
import com.example.movieboxexplorer.ui.DetailScreen
import com.example.movieboxexplorer.ui.DetailUiState
import com.example.movieboxexplorer.ui.DetailViewModel
import com.example.movieboxexplorer.ui.DiagnosticsScreen
import com.example.movieboxexplorer.ui.DiagnosticsViewModel
import com.example.movieboxexplorer.ui.DownloadsScreen
import com.example.movieboxexplorer.ui.DownloadsViewModel
import com.example.movieboxexplorer.ui.HomeScreen
import com.example.movieboxexplorer.ui.HomeViewModel
import com.example.movieboxexplorer.ui.PlaybackHolder
import com.example.movieboxexplorer.ui.PlayerScreen
import com.example.movieboxexplorer.ui.RankingScreen
import com.example.movieboxexplorer.ui.RankingViewModel
import com.example.movieboxexplorer.ui.SearchScreen
import com.example.movieboxexplorer.ui.SearchViewModel
import com.example.movieboxexplorer.ui.theme.MovieBoxTheme
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The poster CDN can reject requests that do not look like they came from
        // the site. Give Coil a browser-like Referer and User-Agent.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                .header("Referer", "https://themoviebox.xyz/")
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
                                )
                                .build()
                            chain.proceed(request)
                        }
                        .build(),
                )
                .build(),
        )

        setContent {
            MovieBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MovieBoxApp()
                }
            }
        }
    }
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_DESTINATIONS = listOf(
    TopDestination("home", "Home", Icons.Default.Home),
    TopDestination("movies", "Movies", Icons.Default.PlayArrow),
    TopDestination("animation", "Animation", Icons.Default.PlayArrow),
    TopDestination("tv", "TV", Icons.Default.Favorite),
    TopDestination("search", "Search", Icons.Default.Search),
)

@Composable
private fun MovieBoxApp() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val homeCache = remember { HomeCache(context) }
    val downloads = remember { Downloads(context) }
    val revision by settings.revision.collectAsStateWithLifecycle()
    val ageGateAccepted by settings.ageGateAcceptedFlow.collectAsStateWithLifecycle()
    val allowAdult by settings.adultContentFlow.collectAsStateWithLifecycle()

    key(revision) {
        val repository = remember(revision) {
            MovieBoxRepository(MovieBoxApi(settings.apiBase, settings.siteHosts(), "https://${settings.siteHost}"))
        }
        val navController = rememberNavController()
        val openDetailScope = rememberCoroutineScope()
        val openDetail: (SubjectBrief) -> Unit = { subject ->
            // Navigate to the exact record the user tapped. Re-ranking by keyword
            // would silently swap in a different title (the "tap Black Clover,
            // open another show" bug). Best-match is only used when the tapped
            // record cannot be opened at all.
            if (subject.detailPath.isNotBlank() && subject.subjectId.isNotBlank()) {
                navController.navigate(
                    "detail/${Uri.encode(subject.detailPath)}/${subject.subjectId}",
                )
            } else {
                openDetailScope.launch {
                    val best = runCatching {
                        repository.resolveBestMatch(subject.title, subject)
                    }.getOrDefault(subject)
                    navController.navigate(
                        "detail/${Uri.encode(best.detailPath)}/${best.subjectId}",
                    )
                }
            }
        }
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        var showSettings by remember { mutableStateOf(false) }

        val host = settings.siteHost

        if (!ageGateAccepted) {
            AdultGateDialog(
                onAccept = { settings.ageGateAccepted = true },
                onDecline = { (context as? Activity)?.finish() },
            )
        }

        Scaffold(
            bottomBar = {
                if (currentRoute in TOP_DESTINATIONS.map { it.route }) {
                    NavigationBar {
                        TOP_DESTINATIONS.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") {
                    val viewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.factory(repository, host, homeCache),
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onOpen = { subject -> openDetail(subject) },
                        onSearch = { navController.navigate("search") },
                        onOpenRanking = { navController.navigate("ranking") },
                        onOpenSettings = { showSettings = true },
                        onBrowse = { route -> navController.navigate(route) },
                    )
                }

                composable("movies") {
                    val viewModel: CatalogViewModel = viewModel(
                        factory = CatalogViewModel.factory(repository, host, CatalogKind.MOVIES),
                    )
                    CatalogScreen(
                        viewModel = viewModel,
                        title = "Movies",
                        onOpen = { subject -> openDetail(subject) },
                    )
                }

                composable("animation") {
                    val viewModel: CatalogViewModel = viewModel(
                        factory = CatalogViewModel.factory(repository, host, CatalogKind.ANIMATED),
                    )
                    CatalogScreen(
                        viewModel = viewModel,
                        title = "Animation",
                        onOpen = { subject -> openDetail(subject) },
                    )
                }

                composable("tv") {
                    val viewModel: CatalogViewModel = viewModel(
                        factory = CatalogViewModel.factory(repository, host, CatalogKind.TV),
                    )
                    CatalogScreen(
                        viewModel = viewModel,
                        title = "TV Shows",
                        onOpen = { subject -> openDetail(subject) },
                    )
                }

                composable("downloads") {
                    val viewModel: DownloadsViewModel = viewModel(
                        factory = DownloadsViewModel.factory(downloads),
                    )
                    DownloadsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable("ranking") {
                    val viewModel: RankingViewModel = viewModel(
                        factory = RankingViewModel.factory(repository),
                    )
                    RankingScreen(
                        viewModel = viewModel,
                        onOpen = { subject -> openDetail(subject) },
                    )
                }

                composable("diagnostics") {
                    val viewModel: DiagnosticsViewModel = viewModel(
                        factory = DiagnosticsViewModel.factory(repository, settings, homeCache),
                    )
                    DiagnosticsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable("search") {
                    val viewModel: SearchViewModel = viewModel(
                        factory = SearchViewModel.factory(repository),
                    )
                    SearchScreen(
                        viewModel = viewModel,
                        onOpen = { subject -> openDetail(subject) },
                        onOpenSettings = { showSettings = true },
                    )
                }

                composable(
                    route = "detail/{path}/{id}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val path = entry.arguments?.getString("path").orEmpty()
                    val id = entry.arguments?.getString("id").orEmpty()
                    val viewModel: DetailViewModel = viewModel(
                        factory = DetailViewModel.factory(repository, path, id),
                    )
                    DetailScreen(
                        viewModel = viewModel,
                        allowAdult = allowAdult,
                        onEnableAdult = { settings.showAdultContent = true },
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { source, label ->
                            PlaybackHolder.set(source)
                            val ready = viewModel.state.value as? DetailUiState.Ready
                            val title = ready?.info?.subject?.title.orEmpty()
                            navController.navigate("player/episode?title=${Uri.encode(title)}")
                        },
                        onPlayTrailer = {
                            val ready = viewModel.state.value as? DetailUiState.Ready
                            val title = ready?.info?.subject?.title.orEmpty()
                            val trailer = ready?.info?.subject?.trailer?.videoAddress?.url
                            val video = trailer?.takeIf { it.isNotBlank() } ?: DEMO_VIDEO
                            navController.navigate(
                                "player/trailer?title=${Uri.encode(title)}&video=${Uri.encode(video)}"
                            )
                        },
                        onOpen = { subject -> openDetail(subject) },
                    )
                }

                composable(
                    route = "player/{kind}?title={title}&video={video}",
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType; defaultValue = "trailer" },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("video") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val kind = entry.arguments?.getString("kind").orEmpty()
                    val title = entry.arguments?.getString("title").orEmpty()
                    val video = entry.arguments?.getString("video").orEmpty()
                    val source by PlaybackHolder.source.collectAsStateWithLifecycle()
                    val episode = kind == "episode"
                    PlayerScreen(
                        title = title,
                        videoUrl = (if (episode) source?.url.orEmpty() else video).ifBlank { DEMO_VIDEO },
                        headers = if (episode) source?.headers.orEmpty() else emptyMap(),
                        subtitle = if (episode) "Now playing: episode" else "Now playing: trailer / demo",
                        onBack = {
                            PlaybackHolder.clear()
                            navController.popBackStack()
                        },
                    )
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettings = false },
                onOpenDiagnostics = {
                    showSettings = false
                    navController.navigate("diagnostics")
                },
                onOpenDownloads = {
                    showSettings = false
                    navController.navigate("downloads")
                },
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    var apiBase by remember { mutableStateOf(settings.apiBase) }
    var siteHost by remember { mutableStateOf(settings.siteHost) }
    var showAdult by remember { mutableStateOf(settings.showAdultContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text(
                    text = "The site rotates its mirror domains. If a title stops " +
                        "resolving, switch the host and search again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = siteHost,
                    onValueChange = { siteHost = it },
                    label = { Text("Site host") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = apiBase,
                    onValueChange = { apiBase = it },
                    label = { Text("API base") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show adult content",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Adult titles are mixed into ordinary rows and " +
                                "search results. They stay hidden until this is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showAdult, onCheckedChange = { showAdult = it })
                }

                TextButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Downloads") }

                TextButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Run diagnostics") }

                TextButton(
                    onClick = {
                        settings.reset()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Reset to defaults") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.siteHost = siteHost
                settings.apiBase = apiBase
                settings.showAdultContent = showAdult
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}