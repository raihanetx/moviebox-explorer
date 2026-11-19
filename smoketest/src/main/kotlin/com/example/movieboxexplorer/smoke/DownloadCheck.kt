package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.loadTitle

/**
 * Verifies that playEpisode resolves DIFFERENT streams for different language
 * tracks — the download flow is only correct if the track's own subjectId /
 * detailPath is honoured.
 *
 * Usage: gradle :smoketest:run --args="download-check \"baki hanma\""
 */
suspend fun cmd_download_check(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {
    val query = args.getOrNull(1) ?: "baki hanma"
    val results = repository.search(query, subjectType = 2)
    val first = results.firstOrNull { it.detailPath.isNotBlank() } ?: results.firstOrNull()
    if (first == null) { println("no search results for $query"); return }
    val info = repository.loadTitle(first.detailPath, first.subjectId) ?: run { println("no detail"); return }
    println("title=${info.subject.title} parent=${info.subject.detailPath} id=${info.subject.subjectId}")
    info.languages.forEachIndexed { i, lang ->
        println("=== language[$i] ${lang.label} | code=${lang.code} | path=\"${lang.detailPath}\" | id=${lang.subjectId} | eps=${lang.episodes} | original=${lang.isOriginal}")
        if (lang.detailPath.isNotBlank()) {
            val res = repository.playEpisode(lang.detailPath, lang.subjectId, season = 1, episode = 1)
            val url = (res as? com.example.movieboxexplorer.data.PlaybackResult.Ready)?.source?.url.orEmpty()
            val masked = if (url.isBlank()) "NO-URL" else url.substringBefore("?").takeLast(40)
            println("    -> ${res::class.simpleName} : $masked")
        }
    }
    api.close()
    return
}
