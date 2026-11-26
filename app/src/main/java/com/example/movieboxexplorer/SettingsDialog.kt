package com.example.movieboxexplorer

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.movieboxexplorer.data.GridCache
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
@Composable
internal fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSaved: () -> Unit = {},
) {
    var apiBase by remember { mutableStateOf(settings.apiBase) }
    var siteHost by remember { mutableStateOf(settings.siteHost) }
    var showAdult by remember { mutableStateOf(settings.showAdultContent) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf("") }
    var updateAvailable by remember { mutableStateOf(false) }

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
                    onClick = onOpenSaved,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Saved") }

                TextButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Run diagnostics") }

                // In-app update: check the public release, auto-download, prompt install.
                TextButton(
                    onClick = {
                        scope.launch {
                            updateStatus = "Checking…"
                            val info = UpdateChecker.check(context)
                            updateAvailable = info.available
                            updateStatus = info.message
                            if (info.available) {
                                updateStatus = info.message + " — downloading…"
                                runCatching {
                                    val apk = UpdateChecker.downloadApk(context, info.apkUrl)
                                    updateStatus = "Download ready — confirm install"
                                    UpdateChecker.launchInstaller(context, apk)
                                }.onFailure {
                                    updateStatus = "Download failed: ${it.message ?: "error"}"
                                    UpdateChecker.openReleasesPage(context)
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Check for updates") }
                if (updateStatus.isNotBlank()) {
                    Text(
                        text = updateStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateAvailable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

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