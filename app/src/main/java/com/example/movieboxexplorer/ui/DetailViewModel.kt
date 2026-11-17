package com.example.movieboxexplorer.ui
import com.example.movieboxexplorer.data.loadTitle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.LanguageFilter
import com.example.movieboxexplorer.data.LanguageInfo
import com.example.movieboxexplorer.data.DownloadMeta
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.PlaybackResult
import com.example.movieboxexplorer.data.PlaybackSource
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.data.TitleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Ready(val info: TitleInfo) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

/** The episode the user picked in the picker. */
data class EpisodeSelection(
    val language: LanguageInfo,
    val season: Int,
    val episode: Int,
)

/** A resolved episode ready to be queued in the system download manager. */
data class DownloadRequest(
    val title: String,
    val url: String,
    val headers: Map<String, String>,
    val fileName: String,
    val meta: DownloadMeta,
)

sealed interface PlaybackUiState {
    data object Idle : PlaybackUiState
    data object Resolving : PlaybackUiState
    data class Ready(val source: PlaybackSource, val label: String) : PlaybackUiState
    data class Failed(val message: String) : PlaybackUiState
}

class DetailViewModel(
    private val repository: MovieBoxRepository,
    private val detailPath: String,
    private val subjectId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _related = MutableStateFlow<List<SubjectBrief>>(emptyList())
    val related: StateFlow<List<SubjectBrief>> = _related.asStateFlow()

    private val _selection = MutableStateFlow<EpisodeSelection?>(null)
    val selection: StateFlow<EpisodeSelection?> = _selection.asStateFlow()

    private val _playback = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    private val _download = MutableStateFlow<DownloadRequest?>(null)
    val download: StateFlow<DownloadRequest?> = _download.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = try {
                val info = repository.loadTitle(detailPath, subjectId)
                    ?.let { it.copy(languages = visibleLanguages(it)) }
                if (info == null) {
                    DetailUiState.Error("This title is not available right now")
                } else {
                    _selection.value = info.languages.firstOrNull()?.let { language ->
                        EpisodeSelection(language, firstSeasonOf(language), 1)
                    }
                    DetailUiState.Ready(info)
                }
            } catch (e: Exception) {
                DetailUiState.Error(e.message ?: "Failed to load title")
            }
        }

        viewModelScope.launch {
            runCatching { repository.related(subjectId) }
                .onSuccess { _related.value = CatalogGrouper.dedupeForDisplay(it) }
            }
    }

    // ---------------------------------------------------------------- selection

    fun selectLanguage(language: LanguageInfo) {
        val current = _selection.value
        val season = if (current?.language?.code == language.code && current.language.detailPath == language.detailPath) {
            current.season
        } else {
            firstSeasonOf(language)
        }
        _selection.value = EpisodeSelection(language, season, 1)
        _playback.value = PlaybackUiState.Idle
    }

    fun selectSeason(season: Int) {
        val current = _selection.value ?: return
        val available = episodesIn(current.language, season)
        val episode = if (current.episode in 1..available) current.episode else 1
        _selection.value = current.copy(season = season, episode = episode)
        _playback.value = PlaybackUiState.Idle
    }

    fun selectEpisode(episode: Int) {
        val current = _selection.value ?: return
        _selection.value = current.copy(episode = episode)
        _playback.value = PlaybackUiState.Idle
    }

    /** Seasons the selected track actually covers. */
    fun seasonsOf(language: LanguageInfo): List<Int> =
        language.seasons.filter { it.episodes > 0 }.map { it.season }.sorted()

    /** Episodes the selected track has in one season. */
    fun episodesIn(language: LanguageInfo, season: Int): Int =
        language.seasons.firstOrNull { it.season == season }?.episodes ?: 0

    /**
     * Qualities the selected track carries in one season, highest first.
     * Empty when the uploader did not report any per-quality counts — the caller
     * then offers a single "best available" option.
     */
    fun resolutionsFor(language: LanguageInfo, season: Int): List<Int> =
        language.seasons
            .firstOrNull { it.season == season }
            ?.resolutions
            ?.map { it.resolution }
            ?.filter { it > 0 }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()

    private fun firstSeasonOf(language: LanguageInfo): Int =
        seasonsOf(language).firstOrNull() ?: 1

    /**
     * The language picker only shows the original track and English/Hindi dubs.
     * Subtitles are not shown at all.
     */
    private fun visibleLanguages(info: TitleInfo): List<LanguageInfo> =
        LanguageFilter.visible(info.languages)

    // ---------------------------------------------------------------- playback

    /**
     * Resolves the selected episode to a playable source.
     *
     * Every language track is its own record with its own upload, so the request is
     * addressed at the track's own subject — the parent subject only ever holds the
     * original upload.
     */
    fun play() {
        val selection = _selection.value ?: return
        viewModelScope.launch {
            _playback.value = PlaybackUiState.Resolving
            val result = repository.playEpisode(
                detailPath = selection.language.detailPath.ifBlank { detailPath },
                subjectId = selection.language.subjectId.ifBlank { subjectId },
                season = selection.season,
                episode = selection.episode,
            )
            _playback.value = when (result) {
                is PlaybackResult.Ready -> PlaybackUiState.Ready(
                    source = result.source,
                    label = labelFor(selection, result.source),
                )
                is PlaybackResult.NoResource -> PlaybackUiState.Failed(
                    "Episode ${selection.episode} was never uploaded for this track"
                )
                is PlaybackResult.Locked -> PlaybackUiState.Failed(result.message)
                is PlaybackResult.Failed -> PlaybackUiState.Failed(result.message)
            }
        }
    }

    /** Resolves the selected episode and queues it in the system download manager. */
    fun download(quality: Int? = null) {
        val selection = _selection.value ?: return
        viewModelScope.launch {
            _playback.value = PlaybackUiState.Resolving
            val result = repository.playEpisode(
                detailPath = selection.language.detailPath.ifBlank { detailPath },
                subjectId = selection.language.subjectId.ifBlank { subjectId },
                season = selection.season,
                episode = selection.episode,
                preferredResolution = quality,
            )
            when (result) {
                is PlaybackResult.Ready -> {
                    val ready = _state.value as? DetailUiState.Ready
                    val mediaTitle = ready?.info?.subject?.title?.takeIf { it.isNotBlank() }
                        ?: detailPath
                    val safeName = selection.language.subjectId
                        .ifBlank { subjectId }
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val resolution = result.source.resolution
                    val qualityLabel = if (resolution > 0) "${resolution}p" else result.source.format
                    val fileName = "moviebox_${safeName}_s${selection.season}e${selection.episode}" +
                        (resolution.takeIf { it > 0 }?.let { "_${it}p" }.orEmpty()) +
                        ".mp4"
                    _download.value = DownloadRequest(
                        title = buildString {
                            append(mediaTitle)
                            append(" — ").append(selection.language.label)
                            if (ready?.info?.isMovie != true) {
                                append(" · S${selection.season} E${selection.episode}")
                            }
                            append(" · ").append(qualityLabel)
                        },
                        url = result.source.url,
                        headers = result.source.headers,
                        fileName = fileName,
                        meta = DownloadMeta(
                            mediaTitle = mediaTitle,
                            languageLabel = selection.language.label,
                            season = selection.season,
                            episode = selection.episode,
                            qualityLabel = qualityLabel,
                            resolution = resolution,
                            fileName = fileName,
                            detailPath = selection.language.detailPath.ifBlank { detailPath },
                            subjectId = selection.language.subjectId.ifBlank { subjectId },
                            preferredResolution = quality,
                            isMovie = ready?.info?.isMovie ?: false,
                            coverUrl = ready?.info?.subject?.cover?.url.orEmpty(),
                        ),
                    )
                    _playback.value = PlaybackUiState.Idle
                }

                is PlaybackResult.NoResource -> {
                    _playback.value = PlaybackUiState.Failed(
                        "Episode ${selection.episode} was never uploaded for this track",
                    )
                }

                is PlaybackResult.Locked -> {
                    _playback.value = PlaybackUiState.Failed(result.message)
                }

                is PlaybackResult.Failed -> {
                    _playback.value = PlaybackUiState.Failed(result.message)
                }
            }
        }
    }

    fun clearDownload() {
        _download.value = null
    }

    private fun labelFor(selection: EpisodeSelection, source: PlaybackSource): String {
        val quality = if (source.resolution > 0) "${source.resolution}p" else source.format
        return buildString {
            append(selection.language.label)
            append(" · S").append(selection.season)
            append(" E").append(selection.episode)
            append(" · ").append(quality)
        }
    }

    companion object {
        fun factory(
            repository: MovieBoxRepository,
            detailPath: String,
            subjectId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(repository, detailPath, subjectId) }
        }
    }
}
