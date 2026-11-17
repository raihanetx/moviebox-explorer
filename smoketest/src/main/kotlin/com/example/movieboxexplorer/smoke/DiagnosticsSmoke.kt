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

suspend fun cmd_diagnostics(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val report = repo.diagnostics(

        host = "themoviebox.xyz",

        appVersion = "smoketest",

        apiBase = "https://h5-api.aoneroom.com",

        siteHost = "themoviebox.xyz",

        cacheBytes = 0,

    )

    println(report.text())

    repo.close()

    return

}

suspend fun cmd_mobile(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val mobile = MobileApi()

    val session = mobile.session()

    println("session: ${session?.take(24)?.let { "$it…" } ?: "none"}")



    val subjects = mobile.searchSubjects("black torch")

    println("search: ${subjects.size} results (HTTP ${mobile.lastStatus})")

    mobile.lastBody?.let { println("body: ${it.take(1400)}") }

    subjects.take(5).forEach { println("  ${it.title} | ${it.subjectId} | ${it.detailPath}") }



    subjects.firstOrNull()?.let { subject ->

        val detail = mobile.detail(subject.subjectId)

        println("detail: ${detail?.subject?.title} dubs=${detail?.dubs?.size ?: 0} subjectDubs=${detail?.subject?.dubs?.size ?: 0} (HTTP ${mobile.lastStatus})")

        mobile.lastBody?.let { println("detail body: ${it.take(500)}") }

        detail?.subject?.dubs?.take(8)?.forEach {

            println("  dub ${it.lanName} (${it.lanCode}) original=${it.original} type=${it.type} = ${it.subjectId}")

        }

        detail?.dubs?.take(8)?.forEach {

            println("  top dub ${it.lanName} (${it.language.ifBlank { it.lanCode }}) = ${it.subjectId}")

        }

        val dubs = mobile.dubInfo(subject.subjectId)

        println("dub-info: ${dubs.size} (HTTP ${mobile.lastStatus})")

        mobile.lastBody?.let { println("dub body: ${it.take(300)}") }

        val staff = mobile.staffInfo(subject.subjectId)

        println("staff: ${staff.size} (HTTP ${mobile.lastStatus})")

        val related = mobile.recommend(subject.subjectId)

        println("recommend: ${related.size} (HTTP ${mobile.lastStatus})")

        val comments = mobile.comments(subject.subjectId)

        println("comments: ${comments.size} (HTTP ${mobile.lastStatus})")

        val resource = mobile.seasonInfo(subject.subjectId)

        println("seasons: ${resource?.seasons?.size ?: 0}")

        resource?.seasons?.take(4)?.forEach {

            println("  S${it.se}: maxEp=${it.maxEp} resolutions=${it.resolutions.size}")

        }

        val playInfo = mobile.playInfo(subject.subjectId, 1, 1)

        println("play-info streams: ${playInfo?.streams?.size ?: 0} hasResource=${playInfo?.hasResource}")

        playInfo?.streams?.firstOrNull()?.let {

            println("  first=${it.url.take(110)} promo=${it.isPromo}")

        }

    }



    mobile.close()

    return

}

suspend fun cmd_titles(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val titles = listOf(

        "That Time I Got Reincarnated as a Slime",

        "Jujutsu Kaisen",

        "Spy x Family",

        "Black Torch",

        "Frieren",

    )

    val repo = MovieBoxRepository(MovieBoxApi())

    for (title in titles) {

        val results = repo.search(title, subjectType = 2)

        val first = results.firstOrNull { it.detailPath.isNotBlank() } ?: results.firstOrNull()

        if (first == null) {

            println("=== $title === no result")

            continue

        }

        val info = repo.loadTitle(first.detailPath, first.subjectId)

        if (info == null) {

            println("=== $title === no detail")

            continue

        }

        println("=== $title ===")

        println("  reference: ${info.referenceEpisodes} eps / ${info.referenceSeasons} seasons")

        info.languages.take(6).forEach { language ->

            val seasons = language.seasons

                .filter { it.episodes > 0 }

                .joinToString(" ") { "S${it.season}:${it.episodes}" }

            println(

                "  ${language.label.padEnd(18)} eps=${language.episodes} " +

                    "seasons=${language.seasonCount} [$seasons]",

            )

        }

    }

    repo.close()

    return

}

suspend fun cmd_ssr(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    listOf("/web/movie", "/web/animated-series", "/web/tv-series").forEach { path ->

        val sections = api.pageOperating(path)

        val items = sections.sumOf { it.subjects.size }

        val highRisk = sections.sumOf { section -> section.subjects.count { it.webHighRisk } }

        println("$path -> sections=${sections.size} items=$items highRisk=$highRisk")

        sections.take(5).forEach { println("   ${it.title}: ${it.subjects.size}") }

    }

    api.close()

    return

}

suspend fun cmd_ssrsearch(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    val repo = MovieBoxRepository(api)

    val results = repo.highRiskSearch("insidious")

    println("highRiskSearch insidious -> ${results.size}")

    results.take(10).forEach {

        println("  highRisk=${it.webHighRisk} | ${it.title} | ${it.detailPath} | ${it.cover?.url}")

    }

    api.close()

    return

}

suspend fun cmd_homessr(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    val sections = api.pageOperating("/")

    println("pageOperating('/') -> ${sections.size}")

    sections.take(10).forEach { println("  ${it.title}: ${it.subjects.size} highRisk=${it.subjects.count { s -> s.webHighRisk }}") }

    api.close()

    return

}

