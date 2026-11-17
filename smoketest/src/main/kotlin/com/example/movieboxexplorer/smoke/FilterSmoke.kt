package com.example.movieboxexplorer.smoke
import com.example.movieboxexplorer.data.fetchPages
import com.example.movieboxexplorer.data.parsePageGrid
import com.example.movieboxexplorer.data.parsePageRows
import com.example.movieboxexplorer.data.parsePagePager
import com.example.movieboxexplorer.data.parsePageFilters

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

suspend fun cmd_filters(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    // The filter bar of the website's grid pages: channelId + every filter option.

    val api = MovieBoxApi()

    for (path in listOf("/web/tv-series", "/web/animated-series")) {

        println("=== $path ===")

        for (html in api.fetchPages(path)) {

            val root = NuxtPayload.parse(html) ?: continue

            val resolved = NuxtPayload.resolve(root)

            val filterData = NuxtPayload.findFirst(resolved, "filterItemsData")

            println("  filterItemsData = ${filterData?.toString()?.take(2600)}")

            val sid = NuxtPayload.findFirst(resolved, "subjectList")

            val pager = (sid as? JsonObject)?.get("pager")

            println("  pager = ${pager?.toString()?.take(200)}")

            break

        }

    }

    api.close()

    return

}

suspend fun cmd_filters2(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    // The site's own filter configuration for every catalog page.

    val api = MovieBoxApi()

    for (path in listOf("/web/movie", "/web/tv-series", "/web/animated-series")) {

        println("=== $path ===")

        for (html in api.fetchPages(path)) {

            val root = NuxtPayload.parse(html) ?: continue

            val resolved = NuxtPayload.resolve(root)

            val filterData = NuxtPayload.findFirst(resolved, "filterItemsData") as? JsonObject ?: continue

            val list = filterData["typeList"] as? JsonArray ?: continue

            for (cfg in list) {

                val obj = cfg as? JsonObject ?: continue

                println("  channelId=${obj["channelId"]} channelName=${obj["channelName"]}")

                val items = obj["items"] as? JsonArray ?: continue

                for (g in items) {

                    val go = g as? JsonObject ?: continue

                    val vals = go["filterVals"] as? JsonArray ?: continue

                    val range = go["rangeVals"]

                    println("    ${go["filterType"]} (${vals.size}): ${vals.joinToString(", ") { (it as JsonObject)["id"].toString().trim('"').take(20) }}" +

                        (if (range != null && range.toString() != "null") "  RANGE=$range" else ""))

                }

            }

            break

        }

    }

    api.close()

    return

}

suspend fun cmd_filter(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val api = MovieBoxApi()

    val repo = MovieBoxRepository(api)

    val path = args.getOrNull(1) ?: "/web/tv-series"

    val genre = args.getOrNull(2)

    val country = args.getOrNull(3)

    println("=== filterConfig for $path ===")

    val configs = repo.filterConfig(path)

    configs.forEach { cfg ->

        println("  channelId=${cfg.channelId} (${cfg.channelName}) groups=${cfg.items.size}")

        cfg.items.forEach { group ->

            println("    ${group.filterType}: ${group.filterVals.size} values [${group.filterVals.take(6).joinToString { it.id }}]")

        }

    }

    val filter = CatalogFilter(genre = genre, country = country)

    println("=== grid with filter=$filter ===")

    repeat(2) {

        val grid = repo.seriesGrid("themoviebox.xyz", 1, filter)

        println("  items=${grid.items.size} hasMore=${grid.hasMore} unlisted=${grid.items.count { it.webHighRisk }}")

        grid.items.take(5).forEach { println("     ${it.genre} | ${it.title}") }

    }

    api.close()

    return

}

suspend fun cmd_grid(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    // What the website's flat-grid pages carry, so the app can copy them exactly.

    val api = MovieBoxApi()

    val paths = args.drop(1).ifEmpty { listOf("/web/tv-series", "/web/animated-series") }

    for (path in paths) {

        println("=== $path ===")

        for (html in api.fetchPages(path)) {

            val root = NuxtPayload.parse(html) ?: continue

            val resolved = NuxtPayload.resolve(root)

            val list = NuxtPayload.findFirst(resolved, "subjectList")

            println("  subjectList = ${list?.toString()?.take(600)}")

            for (key in listOf("pager", "hasMore", "total", "totalCount", "page", "pageSize", "nextPage")) {

                val found = NuxtPayload.findFirst(resolved, key)

                if (found != null) println("  $key = ${found.toString().take(160)}")

            }

            val grid = api.parsePageGrid(html)

            println("  grid items = ${grid.size}, unlisted = ${grid.count { it.webHighRisk }}")

            grid.take(4).forEach { println("     ${it.title} | ${it.genre} | ${it.releaseDate}") }

            break

        }

    }

    api.close()

    return

}

suspend fun cmd_website(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    // What the website itself serves, read with the app's own payload reader.

    val api = MovieBoxApi()

    val repo = MovieBoxRepository(api)

    for (path in listOf("/", "/web/movie", "/web/tv-series", "/web/animated-series")) {

        println("=== website $path ===")

        for ((index, html) in api.fetchPages(path).withIndex()) {

            val rows = api.parsePageRows(html)

            val grid = api.parsePageGrid(html)

            println("  page[$index]: ${html.length} bytes, rows=${rows.size}, grid=${grid.size}")

            rows.forEach {

                println(

                    "    ${it.type} | ${it.title} | ${it.subjects.size} | " +

                        "unlisted=${it.subjects.count { s -> s.webHighRisk }}",

                )

            }

            if (grid.isNotEmpty()) {

                println(

                    "    grid: subjects=${grid.size} " +

                        "unlisted=${grid.count { it.webHighRisk }} " +

                        "noGenre=${grid.count { it.genre.isBlank() }}",

                )

                grid.take(6).forEach {

                    println("       ${it.genre.ifBlank { "(none)" }} | ${it.title} | unlisted=${it.webHighRisk}")

                }

            }

        }

    }



    println()

    println("=== what the app shows ===")

    println("-- home --")

    val home = repo.homeRows("themoviebox.xyz")

    println("  rows=${home.operatingList.size}")

    home.operatingList.take(6).forEach {

        println("    ${it.type} | ${it.title} | ${it.subjects.size} | unlisted=${it.subjects.count { s -> s.webHighRisk }}")

    }

    println("-- movies --")

    repo.movieRows("themoviebox.xyz").take(6).forEach {

        println("    ${it.title} | ${it.items.size} | unlisted=${it.items.count { s -> s.webHighRisk }}")

    }

    println("-- series (grid) --")

    val series = repo.seriesGrid("themoviebox.xyz")

    println("    items=${series.items.size} hasMore=${series.hasMore} nextPage=${series.nextPage} " +

        "unlisted=${series.items.count { it.webHighRisk }}")

    series.items.take(6).forEach { println("    ${it.genre} | ${it.title} | unlisted=${it.webHighRisk}") }

    val series2 = repo.seriesGrid("themoviebox.xyz", 2)

    println("    page2 items=${series2.items.size} first=${series2.items.firstOrNull()?.title}")

    println("-- animation (grid) --")

    val animated = repo.animatedGrid("themoviebox.xyz")

    println("    items=${animated.items.size} hasMore=${animated.hasMore} nextPage=${animated.nextPage} " +

        "unlisted=${animated.items.count { it.webHighRisk }}")

    animated.items.take(6).forEach { println("    ${it.genre} | ${it.title} | unlisted=${it.webHighRisk}") }

    api.close()

    return

}

suspend fun cmd_risk(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {


    val repo = MovieBoxRepository(MovieBoxApi())

    val term = args.getOrNull(1) ?: "black torch"

    val results = repo.searchPage(term, subjectType = 2, page = 1, perPage = 30)

    println("search \"$term\" -> ${results.items.size} items, " +

        "${results.items.count { it.webHighRisk }} unlisted")

    results.items.forEach {

        println("  unlisted=${it.webHighRisk}  ${it.title}  ${it.detailPath}")

    }

    return

}

