package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.CatalogRow
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MobileApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.NuxtPayload
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray as jsonArrayOf
import com.example.movieboxexplorer.data.PlaybackResult
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * Runs the exact networking + parsing code the Android app uses against the live
 * catalogue, so the data layer can be verified without a device.
 *
 * Usage: gradle :smoketest:run --args="attack on titan"
 */
fun main(args: Array<String>) = runBlocking {
    val keyword = args.firstOrNull() ?: "black torch"
    val explicitPath = args.getOrNull(1)
    val api = MovieBoxApi()
    val repository = MovieBoxRepository(api)

    if (keyword == "diagnostics") {
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
        return@runBlocking
    }

    if (keyword == "home-parts") {
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
        return@runBlocking
    }

    if (keyword == "home-time") {
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
        return@runBlocking
    }

    if (keyword == "catalog") {
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
        return@runBlocking
    }

    if (keyword == "best") {
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
        return@runBlocking
    }

    if (keyword == "mobile") {
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
                println("  dub ${it.lanName} (${it.lanCode}) = ${it.subjectId}")
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
        return@runBlocking
    }

    if (keyword == "audit-catalog") {
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
        return@runBlocking
    }

    if (keyword == "sweep") {
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
        return@runBlocking
    }

    if (keyword == "titles") {
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
        return@runBlocking
    }

    if (keyword == "rank") {
        val repo = MovieBoxRepository(MovieBoxApi())
        val page = repo.rankingPage()
        println("ranking categories=${page.rankingList.size} items=${page.subjectList.size}")
        page.rankingList.forEach { println("  ${it.name} | id=${it.id}") }
        page.subjectList.take(5).forEach { println("  ${it.title} | ${it.detailPath}") }
        return@runBlocking
    }

    if (keyword == "homessr") {
        val api = MovieBoxApi()
        val sections = api.pageOperating("/")
        println("pageOperating('/') -> ${sections.size}")
        sections.take(10).forEach { println("  ${it.title}: ${it.subjects.size} highRisk=${it.subjects.count { s -> s.webHighRisk }}") }
        api.close()
        return@runBlocking
    }

    if (keyword == "filters") {
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
        return@runBlocking
    }

    if (keyword == "filters2") {
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
        return@runBlocking
    }

    if (keyword == "filter") {
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
        return@runBlocking
    }

    if (keyword == "grid") {
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
        return@runBlocking
    }

    if (keyword == "website") {
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
        return@runBlocking
    }

    if (keyword == "home") {
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
        return@runBlocking
    }

    if (keyword == "ssrsearch") {
        val api = MovieBoxApi()
        val repo = MovieBoxRepository(api)
        val results = repo.highRiskSearch("insidious")
        println("highRiskSearch insidious -> ${results.size}")
        results.take(10).forEach {
            println("  highRisk=${it.webHighRisk} | ${it.title} | ${it.detailPath} | ${it.cover?.url}")
        }
        api.close()
        return@runBlocking
    }

    if (keyword == "ssr") {
        val api = MovieBoxApi()
        listOf("/web/movie", "/web/animated-series", "/web/tv-series").forEach { path ->
            val sections = api.pageOperating(path)
            val items = sections.sumOf { it.subjects.size }
            val highRisk = sections.sumOf { section -> section.subjects.count { it.webHighRisk } }
            println("$path -> sections=${sections.size} items=$items highRisk=$highRisk")
            sections.take(5).forEach { println("   ${it.title}: ${it.subjects.size}") }
        }
        api.close()
        return@runBlocking
    }

    if (keyword == "risk") {
        val repo = MovieBoxRepository(MovieBoxApi())
        val term = args.getOrNull(1) ?: "black torch"
        val results = repo.searchPage(term, subjectType = 2, page = 1, perPage = 30)
        println("search \"$term\" -> ${results.items.size} items, " +
            "${results.items.count { it.webHighRisk }} unlisted")
        results.items.forEach {
            println("  unlisted=${it.webHighRisk}  ${it.title}  ${it.detailPath}")
        }
        return@runBlocking
    }

    if (keyword == "play") {
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
        return@runBlocking
    }

    var detailPath = explicitPath
    var subjectId = ""

    if (detailPath == null) {
        println("=== search: \"$keyword\" ===")
        val results = repository.search(keyword, subjectType = 2)
        if (results.isEmpty()) {
            println("  (no series results)")
        }
        results.take(8).forEach {
            println("  ${it.title} | ${it.releaseDate} | ${it.genre} | ${it.detailPath}")
        }
        val target = results.firstOrNull { it.detailPath.isNotBlank() } ?: results.firstOrNull()
        if (target == null) {
            println("no title found, aborting")
            api.close()
            return@runBlocking
        }
        detailPath = target.detailPath
        subjectId = target.subjectId
        println()
        println("=== detail: ${target.title} (${target.detailPath}) ===")
    } else {
        println("=== detail: direct path $detailPath ===")
    }

    val info = repository.loadTitle(detailPath, subjectId)
    if (info == null) {
        println("  could not resolve title")
        api.close()
        return@runBlocking
    }

    println("  year      : ${info.subject.releaseDate}")
    println("  genre     : ${info.subject.genre}")
    println("  country   : ${info.subject.countryName}")
    println("  imdb      : ${info.subject.imdbRatingValue} (${info.subject.imdbRatingCount})")
    println("  unlisted  : ${info.subject.webHighRisk}")
    println("  adult     : ${info.subject.isPornographic}")
    println("  forbidden : ${info.isForbidden}")
    println("  risk      : ${info.risk.level}")
    println("  reference : ${info.referenceEpisodes} episodes / ${info.referenceSeasons} season(s)")
    println("  languages :")
    info.languages.forEach { language ->
        val missing = info.missingEpisodes(language)
        val missingSeasons = info.missingSeasons(language)
        val status = buildList {
            if (missing > 0) add("missing $missing ep")
            if (missingSeasons > 0) add("missing $missingSeasons season")
        }.joinToString(", ").ifEmpty { "complete" }
        val seasons = language.seasons
            .filter { it.episodes > 0 }
            .joinToString(" ") { "S${it.season}:${it.episodes}" }
        val resolutions = language.resolutions.joinToString(", ") { "${it.resolution}p x${it.epNum}" }
        println(
            "    ${language.label.padEnd(18)} ${language.kind.padEnd(10)} " +
                "eps=${language.episodes.toString().padEnd(3)} seasons=${language.seasonCount} $status" +
                (if (language.isPornographic) " ADULT" else "") +
                (if (seasons.isNotBlank()) "  [$seasons]" else "") +
                (if (resolutions.isNotBlank()) "  [$resolutions]" else "")
        )
    }
    println("  cast      : ${info.stars.size} entries")

    api.close()
}

/** Counts how complete a listing source's metadata is. */
private data class CatalogStats(
    var total: Int = 0,
    var noTitle: Int = 0,
    var noCover: Int = 0,
    var noDetailPath: Int = 0,
    var noReleaseDate: Int = 0,
    var noGenre: Int = 0,
    var noRating: Int = 0,
) {
    fun add(subject: SubjectBrief) {
        total++
        if (subject.title.isBlank()) noTitle++
        if (subject.cover?.url.isNullOrBlank()) noCover++
        if (subject.detailPath.isBlank()) noDetailPath++
        if (subject.releaseDate.isBlank()) noReleaseDate++
        if (subject.genre.isBlank()) noGenre++
        if (subject.imdbRatingValue.isBlank()) noRating++
    }

    fun print(name: String) {
        fun pct(value: Int): String =
            if (total == 0) "0.0" else "%.1f".format(100.0 * value / total)

        println(
            "$name: $total records | missing title=$noTitle cover=$noCover(${pct(noCover)}%) " +
                "detailPath=$noDetailPath(${pct(noDetailPath)}%) date=$noReleaseDate " +
                "genre=$noGenre rating=$noRating",
        )
    }
}
