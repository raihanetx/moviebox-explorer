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

suspend fun cmd_catalog(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val host = "themoviebox.xyz"

    println("=== home ===")

    val home = repository.homePage(host)

    println("  platforms : ${home.platformList.size}")

    println("  sections  : ${home.operatingList.size}")

    println("  featured  : ${home.operatingList.filter { it.type == "BANNER" }.sumOf { it.banner?.items?.size ?: 0 }}")



    println("=== movies ===")

    val movies = repository.movieRows(host)

    movies.forEach { println("  ${it.title}: ${it.items.size} unlisted=${it.items.count { s -> s.webHighRisk }}") }

    println("=== series (grid) ===")

    repository.seriesGrid(host).let {

        println("  ${it.items.size} items, hasMore=${it.hasMore}, unlisted=${it.items.count { s -> s.webHighRisk }}")

    }



    println("=== animation (grid) ===")

    repository.animatedGrid(host).let {

        println("  ${it.items.size} items, hasMore=${it.hasMore}, unlisted=${it.items.count { s -> s.webHighRisk }}")

    }



    println("=== everyone search ===")

    val everyone = repository.everyoneSearch()

    println("  terms: ${everyone.everyoneSearch.joinToString { it.title }}")

    everyone.hot.forEach { println("  hot ${it.name}: ${it.items.size}") }



    api.close()

    return

}

suspend fun cmd_best(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val titles = listOf(

        "Black Clover",

        "Black Clover [Hindi]",

        "Black Torch",

        "Black Torch [Hindi]",

        "Frieren",

        "Frieren [Hindi]",

        "Spy x Family",

        "Spy x Family [Hindi]",

    )

    for (title in titles) {

        val fallback = SubjectBrief(subjectId = "", title = title)

        val best = repo.resolveBestMatch(title, fallback)

        println("$title -> ${best.title} | id=${best.subjectId} | path=${best.detailPath}")

    }

    repo.close()

    return

}

suspend fun cmd_films(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val host = "themoviebox.xyz"

    val repo = MovieBoxRepository(MovieBoxApi())

    val grid = repo.filmGrid(host)

    println("films page1: items=${grid.items.size} hasMore=${grid.hasMore} nextPage=${grid.nextPage}")

    grid.items.take(5).forEach {

        println("  ${it.title} | ${it.releaseDate} | imdb ${it.imdbRatingValue} | ${it.countryName}")

    }

    val filters = repo.filterConfig("/web/film").firstOrNull()?.items.orEmpty()

    println("filters:")

    filters.forEach { group ->

        println(

            "  ${group.title} (${group.filterType}): ${group.filterVals.size} options" +

                (group.rangeVals?.let { " range=${it.minVal}-${it.maxVal}" } ?: "") +

                " showOut=${group.showOut}",

        )

    }

    val filtered = repo.filmGrid(host, 1, CatalogFilter(genre = "Action", sort = "Hottest"))

    println("genre=Action sort=Hottest: items=${filtered.items.size} first=${filtered.items.firstOrNull()?.title}")

    val page2 = repo.filmGrid(host, 2)

    println("page2: items=${page2.items.size} hasMore=${page2.hasMore}")

    repo.close()

    return

}

suspend fun cmd_page_op(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val path = args.getOrNull(1) ?: "/web/film"

    val api = MovieBoxApi()

    val start = System.currentTimeMillis()

    val rows = api.pageOperating(path)

    val first = System.currentTimeMillis() - start

    val start2 = System.currentTimeMillis()

    api.pageOperating(path)

    val second = System.currentTimeMillis() - start2

    println("path=$path rows=${rows.size} first=${first}ms second=${second}ms")

    rows.take(8).forEach {

        println("  ${it.title ?: it.type}: ${it.subjects.size} items")

    }

    api.close()

    return

}

suspend fun cmd_search_inspect(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val term = args.getOrNull(1) ?: "Black Clover"

    val h5Api = MovieBoxApi()

    val mobileApi = MobileApi()

    println("=== H5 search: $term ===")

    h5Api.search(term, page = 1, perPage = 8, subjectType = 0).items.forEachIndexed { i, it ->

        println("$i ${it.title} | id=${it.subjectId} | path=\"${it.detailPath}\" | type=${it.subjectType} | risk=${it.webHighRisk}")

    }

    println("=== Mobile search: $term ===")

    val mobileItems = mobileApi.searchSubjects(term, perPage = 8)

    mobileItems.forEachIndexed { i, it ->

        println("$i ${it.title} | id=${it.subjectId} | path=\"${it.detailPath}\" | type=${it.subjectType} | risk=${it.webHighRisk} | genre=${it.genre} | country=${it.countryName} | year=${it.releaseDate} | dubs=${it.dubs.size}")

    }

    println("=== Tap simulation (resolveDetailTarget) ===")

    val repo = MovieBoxRepository(MovieBoxApi(siteHosts = listOf("themoviebox.xyz")))

    mobileItems.take(4).forEach { item ->

        val target = repo.resolveDetailTarget(item)

        println("tap ${item.title} -> path=\"${target.detailPath}\" id=${target.subjectId}")

    }

    println("=== After display dedup ===")

    CatalogGrouper.dedupeForDisplay(mobileItems).forEachIndexed { i, it ->

        println("$i ${it.title} | id=${it.subjectId}")

    }

    h5Api.close()

    mobileApi.close()

    repo.close()

    return

}

