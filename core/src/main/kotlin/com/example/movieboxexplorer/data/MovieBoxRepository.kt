package com.example.movieboxexplorer.data
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
import com.example.movieboxexplorer.data.tabOperating
import com.example.movieboxexplorer.data.fetchPages
import com.example.movieboxexplorer.data.parsePageGrid
import com.example.movieboxexplorer.data.parsePagePager
import com.example.movieboxexplorer.data.parsePageFilters
import com.example.movieboxexplorer.data.pageOperating
import com.example.movieboxexplorer.data.rankingPage
import com.example.movieboxexplorer.data.filter
import com.example.movieboxexplorer.data.trending
import com.example.movieboxexplorer.data.everyoneSearch
import com.example.movieboxexplorer.data.ranking
import com.example.movieboxexplorer.data.detailRec
import com.example.movieboxexplorer.data.play
import com.example.movieboxexplorer.data.caption
import com.example.movieboxexplorer.data.head
import com.example.movieboxexplorer.data.close

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class MovieBoxRepository(
    internal val api: MovieBoxApi,
    /**
     * The mobile BFF. It is the catalog fallback: the H5 API answers first, and the
     * mobile API is used when a search or a detail record comes back empty. Playback
     * always stays on H5, because mobile `play-info` only returns promo renditions.
     */
    internal val mobile: MobileApi = MobileApi(),
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

    /**
     * Resolve the best matching version for a poster click.
     *
     * The API search is fuzzy and can return a recap, a re-edit, a special or a
     * different year first. This scores the candidates and returns the best one, with
     * a usable `detailPath`; if nothing better is found, it returns the original
     * poster.
     */
    suspend fun resolveBestMatch(title: String, fallback: SubjectBrief): SubjectBrief {
        if (title.isBlank()) return fallback

        val mobileCandidates = runCatching {
            mobile.searchSubjects(title, perPage = 10)
        }.getOrDefault(emptyList())

        // If the mobile API is blocked, use the H5 search results instead. The scoring
        // works on both shapes.
        val candidates = mobileCandidates.ifEmpty {
            runCatching { api.search(title, page = 1, perPage = 10, subjectType = 2).items }
                .getOrDefault(emptyList())
        }

        val best = SearchRank.bestMatch(candidates, title) ?: return fallback
        if (best.subjectId.isBlank() || best.subjectId == fallback.subjectId) return fallback

        val detail = runCatching { mobile.detail(best.subjectId) }.getOrNull()
        val subject = detail?.subject
        val path = subject?.detailPath
            ?.ifBlank { subject.detailUrl.trimEnd('/').substringAfterLast('/') }
            .orEmpty()
            .ifBlank { best.detailPath }

        if (path.isBlank()) return fallback

        return best.copy(
            detailPath = path,
            cover = best.cover ?: fallback.cover,
        )
    }

    /**
     * Resolve the record to open when the user taps a poster.
     *
     * The tapped record is always preferred: keyword re-ranking used to send the
     * user to a different title than the one they clicked. Only when the tapped
     * record carries no `detailPath` (the mobile search returns subjects with a
     * blank path) do we look up the *same* subjectId and derive the slug — the
     * detail endpoint is keyed by subjectId, so the opened page always matches
     * the tap. Keyword re-ranking remains as a last resort when there is nothing
     * to look up.
     */
    suspend fun resolveDetailTarget(subject: SubjectBrief): SubjectBrief {
        if (subject.detailPath.isNotBlank()) return subject
        if (subject.subjectId.isBlank()) {
            return resolveBestMatch(subject.title, subject)
        }
        val detail = runCatching { mobile.detail(subject.subjectId) }.getOrNull()?.subject
        val path = detail?.detailPath.orEmpty().ifBlank {
            detail?.detailUrl.orEmpty().trimEnd('/').substringAfterLast('/')
        }
        if (path.isBlank()) return subject
        return subject.copy(
            detailPath = path,
            cover = subject.cover ?: detail?.cover,
        )
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
    internal val seasonCache = mutableMapOf<String, Resource?>()
    internal val seasonCacheMutex = Mutex()

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
     * The all-films browser, copied from the website's `/web/film` page — every
     * film the site lists (yoruba originals to English blockbusters, not just the
     * trending rows on the movie tab), with the site's own filter bar.
     */
    suspend fun filmGrid(
        host: String,
        page: Int = 1,
        filter: CatalogFilter = CatalogFilter(),
    ): CatalogGrid = gridOf("/web/film", MOVIE_CHANNEL_ID, page, filter, FILM_PAGE_SIZE)

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
        perPage: Int = FILTER_PAGE_SIZE,
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
                perPage = perPage,
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

    internal val ssrCache = mutableListOf<SubjectBrief>()

    internal fun rememberSsr(subjects: List<SubjectBrief>) {
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
    internal suspend fun cachedSeasonInfo(subjectId: String): Resource? {
        seasonCacheMutex.withLock { seasonCache[subjectId]?.let { return it } }
        val value = mobile.seasonInfo(subjectId)
        seasonCacheMutex.withLock { seasonCache[subjectId] = value }
        return value
    }

    /**
     * Loads a title and resolves every language variant against the catalogue so the
     * UI can show, per language, how many seasons and episodes are actually present.
     */
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
        preferredResolution: Int? = null,
    ): PlaybackResult {
        val data = api.play(detailPath, subjectId, season, episode)
            ?: return PlaybackResult.Failed("Could not reach the catalogue")

        data.lockReason()?.let { return PlaybackResult.Locked(it) }

        val headers = mapOf(
            "Referer" to api.cdnReferer(),
            "User-Agent" to PLAYBACK_USER_AGENT,
        )
        val source = selectPlaybackSource(data, headers, preferredResolution)
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

    /** Closes both HTTP clients. */
    fun close() {
        api.close()
        mobile.close()
    }

    internal companion object {
        /** The website drops this row from the home payload client-side. */
        const val HIDDEN_HOME_ROW = "5675838686378840096"

        /** The channel the website's TV grid lists (`channelName: "Series"`). */
        const val TV_CHANNEL_ID = 2

        /** The channel the website's animation grid lists (`channelName: "Anime"`). */
        const val ANIME_CHANNEL_ID = 1006

        /** The channel the website's film grid lists (`channelName: "Movie"`). */
        const val MOVIE_CHANNEL_ID = 1

        /** The page size the website's "load more" asks for. */
        const val FILTER_PAGE_SIZE = 28

        /** The page size the website's film grid uses. */
        const val FILM_PAGE_SIZE = 36

        /** The CDN rejects media requests that do not look like they come from the app. */
        const val PLAYBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }
}
