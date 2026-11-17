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

suspend fun cmd_home_parts(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    val host = "themoviebox.xyz"

    val t0 = System.currentTimeMillis()

    api.ensureSession()

    val t1 = System.currentTimeMillis()

    val home = api.home(host, useSession = false)

    val t2 = System.currentTimeMillis()

    val trending = api.trending(page = 0, useSession = false)

    val t3 = System.currentTimeMillis()

    println("session=${t1 - t0}ms home=${t2 - t1}ms trending=${t3 - t2}ms")

    println("home sections=${home.operatingList.size} trending=${trending.subjectList.size}")

    api.close()

    return

}

suspend fun cmd_home_time(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val start = System.currentTimeMillis()

    val home = repo.homeRows("themoviebox.xyz")

    val cold = System.currentTimeMillis() - start

    val cachedStart = System.currentTimeMillis()

    repo.homeRows("themoviebox.xyz")

    val cached = System.currentTimeMillis() - cachedStart

    println(

        "homeRows: cold=${cold}ms cached=${cached}ms " +

            "sections=${home.operatingList.size} " +

            "suggestions=${home.operatingList.count { it.type == "SUGGESTIONS" }}",

    )

    repo.close()

    return

}

suspend fun cmd_rank(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val page = repo.rankingPage()

    println("ranking categories=${page.rankingList.size} items=${page.subjectList.size}")

    page.rankingList.forEach { println("  ${it.name} | id=${it.id}") }

    page.subjectList.take(5).forEach { println("  ${it.title} | ${it.detailPath}") }

    return

}

suspend fun cmd_home(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val home = repo.homeRows("themoviebox.xyz")

    println("home sections=${home.operatingList.size}")

    home.operatingList.forEach {

        println("  ${it.type} | ${it.title} | ${it.subjects.size} | highRisk=${it.subjects.count { s -> s.webHighRisk }}")

    }

    println("  rows dropped by the site: opIds=" + home.operatingList.map { it.opId }.filter { it.isNotBlank() }.toSet())

    home.operatingList.filter { it.opId.isNotBlank() }.forEach { println("    opId=${it.opId} | ${it.title}") }

    val rows = home.operatingList

        .filter {

            it.subjects.isNotEmpty() &&

                it.type != "BANNER" &&

                it.type != "FILTER" &&

                it.type != "SPORT_LIVE"

        }

        .map { CatalogRow(it.title, it.subjects) }

    println("rows=${rows.size} unlisted=${rows.flatMap { it.items }.count { it.webHighRisk }}")

    return

}

