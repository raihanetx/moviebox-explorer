package com.example.movieboxexplorer.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class MovieBoxRepository(
    private val api: MovieBoxApi,
    /**
     * The mobile BFF. It is the catalog fallback: the H5 API answers first, and the
     * mobile API is used when a search or a detail record comes back empty. Playback
     * always stays on H5, because mobile `play-info` only returns promo renditions.
     */
    private val mobile: MobileApi = MobileApi(),
) {

    suspend fun search(keyword: String, subjectType: Int): List<SubjectBrief> {
        val items = api.search(keyword = keyword, subjectType = subjectType).items
        if (items.isNotEmpty()) return SearchRank.rank(items, keyword)
        return SearchRank.rank(mobile.searchSubjects(keyword, perPage = 20), keyword)
    }

    suspend fun searchPage(
        keyword: String,
        subjectType: Int,
        page: Int = 1,
        perPage: Int = 10,
    ): SearchPage {
        val result = api.search(keyword, page, perPage, subjectType)
        if (result.items.isNotEmpty()) {
            return result.copy(items = SearchRank.rank(result.items, keyword))
        }
        val mobile = mobile.searchPage(keyword, page = page, perPage = perPage)
        return mobile.copy(items = SearchRank.rank(mobile.items, keyword))
    }

    suspend fun suggest(keyword: String): List<String> = api.suggest(keyword)

    suspend fun everyoneSearch(): EveryoneSearchPage = api.everyoneSearch()

    suspend fun related(subjectId: String): List<SubjectBrief> {
        val items = api.detailRec(subjectId = subjectId, perPage = 12)
        if (items.isNotEmpty()) return items
        return mobile.recommend(subjectId)
    }

    suspend fun homePage(host: String): HomePage = api.home(host)

    suspend fun rankingPage(): MostWatchedPage = api.rankingPage()

    suspend fun rankingContent(id: String, page: Int = 1): List<SubjectBrief> =
        api.ranking(id = id, page = page, perPage = 20).subjectList

    /**
     * The trailing "Suggestions" row the website appends to its pages.
     *
     * It is not part of the tab payload — the site fetches `/subject/trending`
     * separately and pushes the result onto the operating list, so the app has to
     * as well.
     */
    private suspend fun suggestionsRow(
        tabId: String? = null,
        page: Int = 0,
        useSession: Boolean = true,
    ): OperatingSection? {
        val subjects = runCatching {
            api.trending(tabId = tabId, page = page, useSession = useSession).subjectList
        }.getOrNull().orEmpty()
        if (subjects.isEmpty()) return null
        return OperatingSection(
            type = "SUGGESTIONS",
            title = "Suggestions",
            titleType = "suggestions",
            subjects = subjects,
        )
    }

    private var homeCache: HomePage? = null

    /** Mobile `season-info` cache — one call per subject per repository. */
    private val seasonCache = mutableMapOf<String, Resource?>()
    private val seasonCacheMutex = Mutex()

    /**
     * Home rows, copied from the website's `/`.
     *
     * The API answers with the same 36 rows the server-rendered page has, plus the
     * trailing "Suggestions" row the site fetches separately. The website also drops
     * one row from the payload client-side, and the app copies that too.
     */
    suspend fun homeRows(host: String): HomePage {
        homeCache?.let { return it }

        // Both endpoints answer with the lightweight client token, so the home page
        // does not have to wait for the 1.8 s session bootstrap.
        val (fromApi, suggestions) = coroutineScope {
            val home = async { api.home(host, useSession = false) }
            val trending = async { suggestionsRow(page = 0, useSession = false) }
            home.await() to trending.await()
        }

        val sections = fromApi.operatingList.filter { it.opId != HIDDEN_HOME_ROW }
        val result = HomePage(
            platformList = fromApi.platformList,
            operatingList = if (suggestions != null) sections + suggestions else sections,
        )
        homeCache = result
        return result
    }

    /**
     * The movie catalog, copied from the website's `/web/movie` page.
     *
     * The website builds that page from the `ONEROOM_MOVIE` tab and appends a
     * trailing "Suggestions" row from `/subject/trending?tabId=ONEROOM_MOVIE&page=1`.
     * The API answers with the same rows, so it is used and the 1.4 MB page is only
     * a fallback.
     */
    suspend fun movieRows(host: String): List<CatalogRow> {
        val rows = buildList {
            runCatching { api.tabOperating("ONEROOM_MOVIE", host) }
                .getOrNull()
                ?.filter { it.subjects.isNotEmpty() && it.type != "BANNER" && it.type != "FILTER" }
                ?.forEach { add(CatalogRow(it.title, it.subjects)) }
            suggestionsRow(tabId = "ONEROOM_MOVIE", page = 1)?.let { add(CatalogRow(it.title, it.subjects)) }
        }
        if (rows.isNotEmpty()) {
            rememberSsr(rows.flatMap { it.items })
            return rows
        }

        val page = runCatching { api.pageOperating("/web/movie") }.getOrNull().orEmpty()
        val pageRows = page
            .filter { it.subjects.isNotEmpty() && it.type != "BANNER" && it.type != "FILTER" }
            .map { CatalogRow(it.title, it.subjects) }
        rememberSsr(page.flatMap { it.subjects })
        return pageRows
    }

    /**
     * The TV series catalog, copied from the website's `/web/tv-series` page.
     */
    suspend fun seriesGrid(
        host: String,
        page: Int = 1,
        filter: CatalogFilter = CatalogFilter(),
    ): CatalogGrid = gridOf("/web/tv-series", TV_CHANNEL_ID, page, filter)

    /** The animation catalog, copied from the website's `/web/animated-series` page. */
    suspend fun animatedGrid(
        host: String,
        page: Int = 1,
        filter: CatalogFilter = CatalogFilter(),
    ): CatalogGrid = gridOf("/web/animated-series", ANIME_CHANNEL_ID, page, filter)

    /**
     * The filter bar of a catalog grid.
     *
     * The configuration lives in the page's payload — `channelId`, the channel the
     * page lists, plus every filter with its allowed values.
     */
    suspend fun filterConfig(path: String): List<FilterConfig> {
        for (html in api.fetchPages(path)) {
            val configs = api.parsePageFilters(html)
            if (configs.isNotEmpty()) return configs
        }
        return emptyList()
    }

    /**
     * Reads one page of a catalog grid.
     *
     * Page 1 is the server-rendered grid — the site's own page, byte for byte, and it
     * is stable across requests. Later pages, like the site's "load more", come from
     * the filter API: same channel, same `perPage` of 28, same filters. That feed is
     * randomised by the server, which is how the website behaves too.
     *
     * A filter active anywhere in the request also forces the API — the server-rendered
     * page always carries the unfiltered grid.
     */
    private suspend fun gridOf(
        path: String,
        channelId: Int,
        page: Int,
        filter: CatalogFilter,
    ): CatalogGrid {
        if (page <= 1 && filter.isEmpty) {
            for (html in api.fetchPages(path)) {
                val items = api.parsePageGrid(html)
                if (items.isEmpty()) continue
                val pager = api.parsePagePager(html)
                rememberSsr(items)
                return CatalogGrid(
                    items = items,
                    hasMore = pager?.hasMore ?: false,
                    nextPage = 2,
                )
            }
        }

        val data = api.filter(
            FilterRequest(
                page = page,
                perPage = FILTER_PAGE_SIZE,
                channelId = channelId,
                genre = filter.genre,
                country = filter.country,
                year = filter.year,
                sort = filter.sort,
                rate = filter.rate,
                classify = filter.classify,
            )
        )
        val pager = data.pager
        rememberSsr(data.items)
        return CatalogGrid(
            items = data.items,
            hasMore = pager?.hasMore ?: false,
            nextPage = pager?.nextPage?.toIntOrNull()?.takeIf { it > page } ?: (page + 1),
        )
    }
    // ---------------------------------------------------------------- SSR cache

    private val ssrCache = mutableListOf<SubjectBrief>()

    private fun rememberSsr(subjects: List<SubjectBrief>) {
        synchronized(ssrCache) {
            ssrCache.addAll(subjects)
        }
    }

    /**
     * Local search over the server-rendered catalog. The public search API hides
     * `webHighRisk` titles; the SSR payload still contains them, so this lets the
     * search screen surface the same titles as the website.
     *
     * Superseded by [searchPage], which returns the hidden titles as well now that
     * the client sends `X-No-High-Risk-Restrict`. Kept as a fallback for the case
     * where the mirror still serves a full SSR payload.
     */
    suspend fun highRiskSearch(keyword: String): List<SubjectBrief> {
        val query = keyword.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        if (ssrCache.isEmpty()) {
            listOf("/web/movie", "/web/animated-series", "/web/tv-series")
                .flatMap { api.pageOperating(it) }
                .forEach { section -> synchronized(ssrCache) { ssrCache.addAll(section.subjects) } }
        }
        return synchronized(ssrCache) {
            ssrCache.filter {
                it.title.lowercase().contains(query) ||
                    it.genre.lowercase().contains(query) ||
                    it.countryName.lowercase().contains(query)
            }
        }
    }


    /** One mobile season-info call per subject, shared by the parent and every dub. */
    private suspend fun cachedSeasonInfo(subjectId: String): Resource? {
        seasonCacheMutex.withLock { seasonCache[subjectId]?.let { return it } }
        val value = mobile.seasonInfo(subjectId)
        seasonCacheMutex.withLock { seasonCache[subjectId] = value }
        return value
    }

    /**
     * Loads a title and resolves every language variant against the catalogue so the
     * UI can show, per language, how many seasons and episodes are actually present.
     */
    suspend fun loadTitle(detailPath: String, subjectId: String): TitleInfo? {
        // H5 is the fast path. Only call the mobile API when H5 is missing an
        // English/Hindi dub or has no season data — otherwise the mobile round-trip
        // makes every detail page feel slow for no benefit.
        val h5Main = api.detail(detailPath, subjectId)
        var main = h5Main
        if (main == null || main.subject?.webHighRisk == true) {
            val mobileDetail = mobile.detail(subjectId)?.toTitleDetail()
            if (mobileDetail?.subject != null) main = mobileDetail
        }
        val resolvedMain = main ?: return null
        var subject = resolvedMain.subject ?: return null

        val h5HasEnglish = subject.dubs.any { it.type == 0 && it.lanCode.equals("en", true) }
        val h5HasHindi = subject.dubs.any { it.type == 0 && it.lanCode.equals("hi", true) }
        val h5SeasonEpisodes = SeasonMath.seasonsOf(resolvedMain.resource).sumOf { it.episodes }

        val needsMobileDubs = subject.dubs.isEmpty() || !h5HasEnglish || !h5HasHindi
        val needsMobileSeasons = resolvedMain.resource == null || h5SeasonEpisodes == 0

        val mobileDubs: List<MobileDub>
        val mobileMainSeasons: Resource?
        if (needsMobileDubs || needsMobileSeasons) {
            val pair = coroutineScope {
                val dubs = async { if (needsMobileDubs) mobile.dubInfo(subjectId) else emptyList() }
                val seasons = async { if (needsMobileSeasons) cachedSeasonInfo(subjectId) else null }
                dubs.await() to seasons.await()
            }
            mobileDubs = pair.first
            mobileMainSeasons = pair.second
        } else {
            mobileDubs = emptyList()
            mobileMainSeasons = null
        }

        // The H5 record's dub list can be partial (for example, a missing Hindi
        // track). The mobile `dub-info` endpoint is the fuller index, so merge it
        // in. A dub with a non-empty detailPath wins over one without.
        val mergedDubs = (subject.dubs + mobileDubs.map { it.toDubVariant() })
            .filter { it.subjectId.isNotBlank() || it.detailPath.isNotBlank() }
            .groupBy { it.subjectId.ifBlank { it.detailPath } }
            .map { (_, variants) -> variants.firstOrNull { it.detailPath.isNotBlank() } ?: variants.first() }
        if (mergedDubs.isNotEmpty()) subject = subject.copy(dubs = mergedDubs)

        // The parent record's mobile `season-info` is the reference for how many
        // episodes a complete track has. If a dub's H5 resource is shorter than
        // that, fetch the dub's own mobile `season-info` and merge it in.
        val mainResource = SeasonMath.mergeResources(
            resolvedMain.resource,
            mobileMainSeasons,
        )
        val ownSeasons = SeasonMath.seasonsOf(mainResource)
        val referenceEpisodeCount = ownSeasons.sumOf { it.episodes }

        val gate = Semaphore(4)
        val languages = coroutineScope {
            subject.dubs.map { variant ->
                async {
                    val resolved = gate.withPermit {
                        api.detail(variant.detailPath, variant.subjectId)
                            ?: mobile.detail(variant.subjectId)?.toTitleDetail()
                    }
                    val h5Resource = resolved?.resource
                    val h5EpisodeCount = SeasonMath.seasonsOf(h5Resource).sumOf { it.episodes }
                    val mobileResource = if (
                        h5Resource == null ||
                        (referenceEpisodeCount > 0 && h5EpisodeCount < referenceEpisodeCount)
                    ) {
                        gate.withPermit { cachedSeasonInfo(variant.subjectId) }
                    } else {
                        null
                    }
                    val resource = SeasonMath.mergeResources(h5Resource, mobileResource)
                    val seasons = SeasonMath.seasonsOf(resource)
                    LanguageInfo(
                        label = variant.lanName.ifBlank { variant.lanCode },
                        code = variant.lanCode,
                        kind = if (variant.type == 1) "Subtitle" else "Dub",
                        isOriginal = variant.original,
                        episodes = seasons.sumOf { it.episodes },
                        seasons = seasons,
                        resolutions = SeasonMath.mergeResolutions(seasons),
                        uploader = resource?.uploadBy.orEmpty(),
                        detailPath = variant.detailPath,
                        subjectId = variant.subjectId,
                        isPornographic = resolved?.subject?.isPornographic == true,
                    )
                }
            }.awaitAll()
        }

        val reference = maxOf(
            referenceEpisodeCount,
            languages.maxOfOrNull { it.episodes } ?: 0,
        )
        val referenceSeasons = maxOf(
            ownSeasons.count { it.episodes > 0 },
            languages.maxOfOrNull { it.seasonCount } ?: 0,
        )

        // A title without language variants — most movies — is its own track, so the
        // picker still has something to play.
        val tracks = languages.ifEmpty {
            listOf(
                LanguageInfo(
                    label = "Original",
                    code = "",
                    kind = "Dub",
                    isOriginal = true,
                    episodes = ownSeasons.sumOf { it.episodes },
                    seasons = ownSeasons,
                    resolutions = SeasonMath.mergeResolutions(ownSeasons),
                    uploader = mainResource?.uploadBy.orEmpty(),
                    detailPath = detailPath,
                    subjectId = subjectId,
                    isPornographic = subject.isPornographic,
                )
            )
        }

        // The cast is optional. Fetching it from the mobile API adds a network call
        // to every detail open, so only the H5 record's cast is used.
        val stars = resolvedMain.stars

        return TitleInfo(
            subject = subject,
            stars = stars.filter { it.name.isNotBlank() },
            languages = tracks.sortedWith(
                compareByDescending<LanguageInfo> { it.episodes }.thenByDescending { it.seasonCount }
            ),
            referenceEpisodes = reference,
            referenceSeasons = referenceSeasons,
            isForbidden = resolvedMain.isForbid,
            resource = mainResource,
        )
    }

    // ---------------------------------------------------------------- playback

    /**
     * Resolves one episode of a language track to a playable source.
     *
     * The catalogue hands out several renditions of the same episode: progressive MP4s
     * (one file per quality level) and a DASH manifest that carries every quality in
     * a single adaptive stream.
     *
     * MP4 is preferred, because the CDN accepts it with nothing but a `Referer`
     * pointing at one of the site's mirror hosts, while the DASH manifest additionally
     * needs the edge cache credential. The adaptive manifest is only used for titles
     * that ship no MP4 at all.
     */
    suspend fun playEpisode(
        detailPath: String,
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
    ): PlaybackResult {
        val data = api.play(detailPath, subjectId, season, episode)
            ?: return PlaybackResult.Failed("Could not reach the catalogue")

        data.lockReason()?.let { return PlaybackResult.Locked(it) }

        val headers = mapOf(
            "Referer" to api.cdnReferer(),
            "User-Agent" to PLAYBACK_USER_AGENT,
        )
        val source = selectPlaybackSource(data, headers)
        if (source != null) return PlaybackResult.Ready(source)

        return if (data.hasResource) {
            PlaybackResult.Failed("No playable rendition for this episode")
        } else {
            PlaybackResult.NoResource
        }
    }

    /** Subtitle tracks of one episode. */
    suspend fun captions(
        detailPath: String,
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
        lan: String = "en",
    ): List<Caption> = api.caption(detailPath, subjectId, season, episode, lan)

    /**
     * Runs a set of live checks against both APIs and the repository.
     *
     * The report is plain text and contains no tokens. It is used by the in-app
     * Diagnostics screen so a device in any region can show exactly which layer is
     * failing.
     */
    suspend fun diagnostics(
        host: String,
        appVersion: String,
        apiBase: String,
        siteHost: String,
        cacheBytes: Long,
    ): DiagnosticsReport {
        val results = mutableListOf<CheckResult>()

        suspend fun check(name: String, block: suspend () -> Pair<CheckStatus, String>) {
            val start = System.currentTimeMillis()
            val result = try {
                val (status, detail) = block()
                CheckResult(name, status, System.currentTimeMillis() - start, detail)
            } catch (e: Exception) {
                CheckResult(name, CheckStatus.FAIL, System.currentTimeMillis() - start, e.message ?: "error")
            }
            results += result
        }

        check("H5 session") {
            val token = api.ensureSession()
            (if (token.isNotBlank()) CheckStatus.PASS else CheckStatus.FAIL) to
                "token ${token.length} chars"
        }

        val home = api.home(host)
        check("H5 home") {
            (if (home.operatingList.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
                "${home.operatingList.size} sections, ${home.platformList.size} platforms"
        }

        check("H5 search") {
            val search = api.search("Black Clover", page = 1, perPage = 3, subjectType = 2)
            val ranked = SearchRank.rank(search.items, "Black Clover")
            val first = ranked.firstOrNull()
            (if (first != null && first.title.contains("Black Clover", true)) CheckStatus.PASS else CheckStatus.WARN) to
                "${search.items.size} results, first=${first?.title.orEmpty()}"
        }

        check("Mobile session") {
            val token = mobile.session()
            (if (!token.isNullOrBlank()) CheckStatus.PASS else CheckStatus.FAIL) to
                "token ${token?.length ?: 0} chars"
        }

        val mobileItems = SearchRank.rank(
            mobile.searchSubjects("Black Clover", perPage = 5),
            "Black Clover",
        )
        check("Mobile search") {
            (if (mobileItems.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
                "${mobileItems.size} results, first=${mobileItems.firstOrNull()?.title.orEmpty()}"
        }

        val mobileFirst = mobileItems.firstOrNull()
        val mobileDetail = mobileFirst?.let { mobile.detail(it.subjectId) }
        check("Mobile detail") {
            val subject = mobileDetail?.subject
            (if (subject != null) CheckStatus.PASS else CheckStatus.FAIL) to
                "${subject?.title.orEmpty()}, dubs=${mobileDetail?.dubs?.size ?: 0}"
        }

        check("Mobile dubs") {
            val dubs = mobileFirst?.let { mobile.dubInfo(it.subjectId) }.orEmpty()
            (if (dubs.isNotEmpty()) CheckStatus.PASS else CheckStatus.WARN) to
                dubs.joinToString(", ") { it.lanName }
        }

        check("Mobile seasons") {
            val resource = mobileFirst?.let { mobile.seasonInfo(it.subjectId) }
            (if (!resource?.seasons.isNullOrEmpty()) CheckStatus.PASS else CheckStatus.WARN) to
                "${resource?.seasons?.size ?: 0} seasons"
        }

        val mobileSubject = mobileDetail?.subject
        val detailPath = mobileSubject?.detailPath
            ?.ifBlank { mobileSubject.detailUrl.trimEnd('/').substringAfterLast('/') }
            .orEmpty()
            .ifBlank { "black-torch-ezMof4UGRj8" }
        val detailId = mobileSubject?.subjectId
            ?.ifBlank { mobileFirst?.subjectId }
            .orEmpty()
            .ifBlank { "6983322689440465144" }

        check("Detail path") {
            val info = loadTitle(detailPath, detailId)
            (if (info != null && info.languages.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
                "${info?.languages?.size ?: 0} languages, ${info?.referenceEpisodes ?: 0} episodes"
        }

        check("Playback") {
            val data = api.play(detailPath, detailId, 1, 1)
            if (data == null) {
                CheckStatus.FAIL to "no response"
            } else {
                val source = selectPlaybackSource(
                    data,
                    mapOf(
                        "Referer" to api.cdnReferer(),
                        "User-Agent" to PLAYBACK_USER_AGENT,
                    ),
                )
                when {
                    source == null -> CheckStatus.FAIL to "no playable source (hasResource=${data.hasResource})"
                    source.url.contains("macdn.aoneroom.com/other", true) -> CheckStatus.FAIL to "promo stream"
                    else -> CheckStatus.PASS to "${source.format} ${source.resolution}p"
                }
            }
        }

        check("Home cache") {
            (if (cacheBytes > 0) CheckStatus.PASS else CheckStatus.WARN) to
                "${cacheBytes / 1024} KB"
        }

        check("Image CDN") {
            val image = home.operatingList
                .firstOrNull { it.type == "BANNER" }
                ?.banner?.items?.firstOrNull()?.image?.url
            if (image.isNullOrBlank()) {
                CheckStatus.SKIP to "no banner image"
            } else {
                (if (api.head(image)) CheckStatus.PASS else CheckStatus.WARN) to image.take(80)
            }
        }

        return DiagnosticsReport(
            appVersion = appVersion,
            apiBase = apiBase,
            siteHost = siteHost,
            cacheBytes = cacheBytes,
            results = results,
        )
    }

    /** Closes both HTTP clients. */
    fun close() {
        api.close()
        mobile.close()
    }

    private companion object {
        /** The website drops this row from the home payload client-side. */
        const val HIDDEN_HOME_ROW = "5675838686378840096"

        /** The channel the website's TV grid lists (`channelName: "Series"`). */
        const val TV_CHANNEL_ID = 2

        /** The channel the website's animation grid lists (`channelName: "Anime"`). */
        const val ANIME_CHANNEL_ID = 1006

        /** The page size the website's "load more" asks for. */
        const val FILTER_PAGE_SIZE = 28

        /** The CDN rejects media requests that do not look like they come from the app. */
        const val PLAYBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }
}
