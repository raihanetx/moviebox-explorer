package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.CatalogRow
import com.example.movieboxexplorer.data.CatalogValidator
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MobileApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.NuxtPayload
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray as jsonArrayOf
import com.example.movieboxexplorer.data.PlaybackResult
import com.example.movieboxexplorer.data.SearchRank
import kotlin.random.Random
import com.example.movieboxexplorer.data.loadTitle
import com.example.movieboxexplorer.data.detail
import com.example.movieboxexplorer.data.searchSubjects
import com.example.movieboxexplorer.data.searchPage
import com.example.movieboxexplorer.data.seasonInfo
import com.example.movieboxexplorer.data.dubInfo
import com.example.movieboxexplorer.data.staffInfo
import com.example.movieboxexplorer.data.recommend
import com.example.movieboxexplorer.data.rankingList
import com.example.movieboxexplorer.data.rankingSubjects
import com.example.movieboxexplorer.data.comments
import com.example.movieboxexplorer.data.playInfo
import com.example.movieboxexplorer.data.home
import com.example.movieboxexplorer.data.trending
import com.example.movieboxexplorer.data.pageOperating
import com.example.movieboxexplorer.data.diagnostics
import com.example.movieboxexplorer.data.close

suspend fun cmd_play(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    val repo = MovieBoxRepository(api)

    val path = args.getOrNull(1) ?: "black-torch-ezMof4UGRj8"

    val subjectId = args.getOrNull(2) ?: "6983322689440465144"

    val season = args.getOrNull(3)?.toIntOrNull() ?: 1

    val episode = args.getOrNull(4)?.toIntOrNull() ?: 1



    println("=== play $path s$season e$episode ===")

    when (val result = repo.playEpisode(path, subjectId, season, episode)) {

        is PlaybackResult.Ready -> {

            val source = result.source

            println("  format      : ${source.format}")

            println("  resolution  : ${source.resolution}p")

            println("  codec       : ${source.codec}")

            println("  duration    : ${source.durationSeconds}s")

            println("  url         : ${source.url}")

            source.headers.forEach { (k, v) ->

                println("  header      : $k = ${v.take(70)}${if (v.length > 70) "…" else ""}")

            }

            println("  captions    : ${repo.captions(path, subjectId, season, episode).size}")

        }

        is PlaybackResult.NoResource -> println("  no resource for this episode")

        is PlaybackResult.Locked -> println("  locked: ${result.message}")

        is PlaybackResult.Failed -> println("  failed: ${result.message}")

    }

    api.close()

    return

}

