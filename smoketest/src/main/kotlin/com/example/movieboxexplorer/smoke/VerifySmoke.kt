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

suspend fun cmd_audit_catalog(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val host = "themoviebox.xyz"

    val all = mutableListOf<SubjectBrief>()



    fun collect(name: String, subjects: List<SubjectBrief>) {

        val stats = CatalogStats()

        subjects.forEach { stats.add(it) }

        stats.print(name)

        all += subjects

    }



    collect("home", repo.homeRows(host).operatingList.flatMap { it.subjects })

    collect("movies", repo.movieRows(host).flatMap { it.items })

    collect("tv", repo.seriesGrid(host).items)

    collect("animation", repo.animatedGrid(host).items)

    collect("ranking", repo.rankingPage().subjectList)



    val unique = all.distinctBy { it.subjectId }.filter { it.subjectId.isNotBlank() }

    val stats = CatalogStats()

    unique.forEach { stats.add(it) }

    stats.print("UNIQUE")

    println("listing records: ${all.size}, unique subjects: ${unique.size}")

    repo.close()

    return

}

suspend fun cmd_sweep(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val limit = args.getOrNull(1)?.toIntOrNull() ?: 50

    val repo = MovieBoxRepository(MovieBoxApi())

    val host = "themoviebox.xyz"

    val candidates = buildList {

        addAll(repo.homeRows(host).operatingList.flatMap { it.subjects })

        addAll(repo.movieRows(host).flatMap { it.items })

        addAll(repo.seriesGrid(host).items)

        addAll(repo.animatedGrid(host).items)

    }

        .distinctBy { it.subjectId }

        .filter { it.subjectId.isNotBlank() && it.detailPath.isNotBlank() }



    val sample = candidates.shuffled(Random(42)).take(limit)

    println("sweep: candidates=${candidates.size} sample=${sample.size}")



    var ok = 0

    var noDubs = 0

    var zeroEpisodes = 0

    var noSeasons = 0

    var errors = 0

    val failures = mutableListOf<String>()

    val zeroEpisodeTitles = mutableListOf<String>()

    val noSeasonTitles = mutableListOf<String>()



    sample.forEach { subject ->

        try {

            val info = repo.loadTitle(subject.detailPath, subject.subjectId)

            if (info == null) {

                errors++

                failures += "${subject.title}: no detail"

                return@forEach

            }

            ok++

            if (info.languages.isEmpty()) noDubs++

            if (!info.isMovie && info.languages.isNotEmpty() && info.languages.all { it.episodes == 0 }) {

                zeroEpisodes++

                zeroEpisodeTitles += subject.title

            }

            if (!info.isMovie && info.referenceSeasons == 0) {

                noSeasons++

                noSeasonTitles += subject.title

            }

        } catch (e: Exception) {

            errors++

            failures += "${subject.title}: ${e.message}"

        }

    }



    val rate = if (sample.isEmpty()) 0 else ok * 100 / sample.size

    println(

        "sweep: ok=$ok/${sample.size} ($rate%) noDubs=$noDubs zeroEpisodes=$zeroEpisodes " +

            "noSeasons=$noSeasons errors=$errors",

    )

    failures.take(15).forEach { println("  FAIL $it") }

    if (zeroEpisodeTitles.isNotEmpty()) println("  zero episodes: ${zeroEpisodeTitles.joinToString(", ")}")

    if (noSeasonTitles.isNotEmpty()) println("  no seasons: ${noSeasonTitles.joinToString(", ")}")

    repo.close()

    return

}

suspend fun cmd_validate_title(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val title = args.getOrNull(1) ?: "Black Clover"

    val repo = MovieBoxRepository(MovieBoxApi())

    val results = repo.search(title, subjectType = 2)

    val groups = CatalogGrouper.group(results)

    val group = groups.firstOrNull { it.title.contains(title, ignoreCase = true) }

        ?: groups.firstOrNull()



    if (group == null) {

        println("no group for $title")

        repo.close()

        return

    }



    val validation = CatalogValidator.validateGroup(group, repo)

    println("group: ${group.title} | records=${group.records.size}")

    println("languages=${validation.languages} seasons=${validation.seasons} eps=${validation.episodes}")

    println("issues=${validation.issues.joinToString("; ").ifBlank { "none" }}")

    repo.close()

    return

}

suspend fun cmd_validate_catalog(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val host = "themoviebox.xyz"

    val records = buildList {

        addAll(repo.homeRows(host).operatingList.flatMap { it.subjects })

        addAll(repo.movieRows(host).flatMap { it.items })

        addAll(repo.seriesGrid(host).items)

        addAll(repo.animatedGrid(host).items)

    }.distinctBy { it.subjectId }



    val groups = CatalogGrouper.group(records)

    println("records=${records.size} groups=${groups.size}")

    groups.take(10).forEach {

        println("  ${it.title} <- ${it.records.size} records")

    }



    val summary = CatalogValidator.validateAll(groups, repo, limit = 10)

    println(

        "validation: groups=${summary.groups} records=${summary.records} " +

            "clean=${summary.clean} warnings=${summary.warnings}",

    )

    summary.results.forEach { result ->

        println(

            "  ${result.title}: languages=${result.languages} seasons=${result.seasons} " +

                "eps=${result.episodes} issues=${result.issues.joinToString("; ")}",

        )

    }

    repo.close()

    return

}

