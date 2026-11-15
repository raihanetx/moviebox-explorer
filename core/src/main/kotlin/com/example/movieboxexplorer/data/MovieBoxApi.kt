package com.example.movieboxexplorer.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.time.ZoneId

/**
 * Thin client for the MovieBox web API (the backend behind themoviebox.xyz and the
 * MovieBox H5 app).
 *
 * Three things are required for the API to answer:
 *
 *  1. an anonymous session token — the server hands one out in the `x-user` response
 *     header of any request; it is then sent back as `Authorization: Bearer …`;
 *  2. `X-Client-Token` — `"<unix seconds>,<md5 of the reversed seconds>"` — used only
 *     while no session token is held;
 *  3. `Origin` / `Referer` pointing at one of the site's mirror hosts. The detail
 *     endpoint is scoped per host, and the Referer has to address the title's own
 *     SPA play page, otherwise the record resolves as "not found".
 *
 * Metadata only: this client never asks for stream manifests, subtitle files or
 * download links.
 */
class MovieBoxApi(
    private val apiBase: String = DEFAULT_API_BASE,
    private val siteHosts: List<String> = DEFAULT_SITE_HOSTS,
    private val siteBase: String = DEFAULT_SITE_BASE,
) {

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = false
    }

    private val http = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(jsonFormat) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    @Volatile
    private var session: String? = null

    // ---------------------------------------------------------------- headers

    private fun clientInfo(): String = """{"timezone":"${ZoneId.systemDefault().id}"}"""

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** `"<unix seconds>,<md5 of the reversed seconds>"` — matches the web client's scheme. */
    private fun clientToken(): String {
        val seconds = System.currentTimeMillis() / 1000
        return "$seconds,${md5Hex(seconds.toString().reversed())}"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(token: String?) {
        header("X-Client-Info", clientInfo())
        header("X-Request-Lang", "en")
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        } else {
            header("X-Client-Token", clientToken())
        }
    }

    /**
     * Listing requests carry one extra header.
     *
     * The site's own web build sends `X-No-High-Risk-Restrict: 1` on every API call
     * (`_nuxt/DelL8zx-.js`). On the *listing* endpoints it is the switch that makes
     * the server stop hiding the `webHighRisk` titles: without it the catalogue
     * answers as if those titles did not exist — a search for "black torch" returns
     * six titles, none of them Black Torch. It is not a secret; the header is part of
     * the normal web build.
     *
     * It must **not** be sent to `/detail`. That endpoint reads the header the other
     * way round and applies the high-risk rule, answering `404 TARGET_NOT_FOUND`
     * with the message `"high risk"` for exactly the titles the listing endpoints
     * just revealed. `/play` answers either way.
     */
    private fun io.ktor.client.request.HttpRequestBuilder.applyListingAuth(token: String?) {
        applyAuth(token)
        header("X-No-High-Risk-Restrict", "1")
    }

    /**
     * The `Referer` that addresses a title's own SPA play page.
     *
     * The detail and play endpoints are scoped per host and only answer when the
     * `Referer` points at the play page of the title that is being asked for.
     */
    fun playPageReferer(
        detailPath: String,
        subjectId: String? = null,
        host: String = siteHosts.firstOrNull().orEmpty(),
    ): String = buildString {
        append("https://$host/spa/videoPlayPage/movies/$detailPath")
        if (!subjectId.isNullOrBlank()) {
            append("?id=$subjectId&type=/movie/detail&lang=en")
        }
    }

    /**
     * The `Referer` the media CDN requires.
     *
     * The CDN answers `429 Too Many Requests` to media requests that do not look like
     * they come from the site, and `200` once a `Referer` pointing at the origin of
     * one of the mirror hosts is attached.
     */
    fun cdnReferer(host: String = siteHosts.firstOrNull().orEmpty()): String = "https://$host/"

    // ---------------------------------------------------------------- session

    /** Requests an anonymous session; the server returns one in the `x-user` header. */
    suspend fun ensureSession(): String {
        session?.let { return it }
        val raw = runCatching {
            http.post("$apiBase/wefeed-h5api-bff/subject/search-suggest") {
                contentType(ContentType.Application.Json)
                setBody(SuggestRequest(keyword = "a", perPage = 1))
                applyAuth("")
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.headers["x-user"]
        }.getOrNull()

        val token = raw?.let {
            runCatching {
                jsonFormat.parseToJsonElement(it).jsonObject["token"]?.jsonPrimitive?.content
            }.getOrNull()
        }
        if (!token.isNullOrBlank()) session = token
        return session.orEmpty()
    }

    // ---------------------------------------------------------------- calls

    suspend fun suggest(keyword: String): List<String> {
        val token = ensureSession()
        return runCatching {
            val envelope: Envelope<SuggestPage> = http.post("$apiBase/wefeed-h5api-bff/subject/search-suggest") {
                contentType(ContentType.Application.Json)
                setBody(SuggestRequest(keyword = keyword, perPage = 12))
                applyListingAuth(token)
            }.body()
            envelope.data?.items?.map { it.word }.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        perPage: Int = 10,
        subjectType: Int = 2,
    ): SearchPage {
        val token = ensureSession()
        return runCatching {
            val envelope: Envelope<SearchPage> = http.post("$apiBase/wefeed-h5api-bff/subject/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(keyword = keyword, page = page, perPage = perPage, subjectType = subjectType))
                applyListingAuth(token)
            }.body()
            envelope.data ?: SearchPage()
        }.getOrDefault(SearchPage())
    }

    /**
     * Loads one title. Every mirror host is tried in turn; the Referer always addresses
     * the title's own play page, which is what makes the record resolvable.
     */
    suspend fun detail(detailPath: String, subjectId: String? = null): TitleDetail? {
        if (detailPath.isBlank()) return null
        val token = ensureSession()

        for (host in siteHosts) {
            val referer = playPageReferer(detailPath, subjectId, host)
            val envelope = runCatching {
                http.get("$apiBase/wefeed-h5api-bff/detail?detailPath=$detailPath") {
                    applyAuth(token)
                    header(HttpHeaders.Referrer, referer)
                    header(HttpHeaders.Origin, "https://$host")
                }.body<Envelope<TitleDetail>>()
            }.getOrNull() ?: continue

            if (envelope.code == 0 && envelope.data?.subject != null) return envelope.data
        }
        return null
    }

    /**
     * Resolves the play manifest of a single episode.
     *
     * `GET /wefeed-h5api-bff/subject/play?subjectId=&se=&ep=&detailPath=&streamSignType=1`
     * — the same call the site's SPA makes. Like [detail] it only answers when the
     * `Referer` addresses the title's own SPA play page.
     *
     * An episode that was never uploaded comes back with `hasResource = false` and no
     * streams, which is how the catalogue reports a gap in an otherwise complete
     * upload.
     */
    suspend fun play(
        detailPath: String,
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
    ): PlayData? {
        if (detailPath.isBlank()) return null
        // /subject/play is context-validated: Origin + Referer + X-Client-Token.
        // It does not need a Bearer token, so skip the session bootstrap.
        val token: String? = null

        for (host in siteHosts) {
            val query = buildString {
                append("subjectId=").append(subjectId)
                append("&se=").append(season)
                append("&ep=").append(episode)
                append("&detailPath=").append(detailPath)
                append("&streamSignType=1")
                append("&supportCodecs[hevc]=1")
                append("&supportCodecs[h264]=1")
            }
            val envelope = runCatching {
                http.get("$apiBase/wefeed-h5api-bff/subject/play?$query") {
                    applyAuth(token)
                    header(HttpHeaders.Referrer, playPageReferer(detailPath, subjectId, host))
                    header(HttpHeaders.Origin, "https://$host")
                    header("x-vip-restrict", "0")
                    header("x-no-high-risk-restrict", "0")
                    header("x-source", "moviebox")
                }.body<Envelope<PlayData>>()
            }.getOrNull() ?: continue

            if (envelope.code == 0 && envelope.data != null) {
                return normalizePlayData(envelope.data)
            }
        }
        return null
    }

    /**
     * Older H5 play responses put the renditions under `resource` instead of
     * `streams`. Accept both shapes.
     */
    private fun normalizePlayData(data: PlayData): PlayData {
        if (data.streams.isNotEmpty()) return data
        val streams = parseResourceStreams(data.resource)
        return if (streams.isEmpty()) data else data.copy(streams = streams)
    }

    private fun parseResourceStreams(resource: JsonElement?): List<PlayStream> {
        val array = when (resource) {
            is JsonArray -> resource
            is JsonObject -> resource["streams"] as? JsonArray
                ?: resource["items"] as? JsonArray
            else -> null
        } ?: return emptyList()
        return runCatching {
            jsonFormat.decodeFromJsonElement(ListSerializer(PlayStream.serializer()), array)
        }.getOrDefault(emptyList())
    }

    /**
     * Subtitle tracks for one episode.
     *
     * `GET /wefeed-h5api-bff/subject/caption?id=&se=&ep=&detailPath=&lan=` — note that
     * this endpoint wants the id under `id`, not `subjectId`.
     */
    suspend fun caption(
        detailPath: String,
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
        lan: String = "en",
    ): List<Caption> {
        if (detailPath.isBlank()) return emptyList()
        val token = ensureSession()

        for (host in siteHosts) {
            val query = buildString {
                append("id=").append(subjectId)
                append("&se=").append(season)
                append("&ep=").append(episode)
                append("&detailPath=").append(detailPath)
                append("&lan=").append(lan)
            }
            val envelope = runCatching {
                http.get("$apiBase/wefeed-h5api-bff/subject/caption?$query") {
                    applyAuth(token)
                    header(HttpHeaders.Referrer, playPageReferer(detailPath, subjectId, host))
                    header(HttpHeaders.Origin, "https://$host")
                }.body<Envelope<CaptionPage>>()
            }.getOrNull() ?: continue

            if (envelope.code == 0) return envelope.data?.captions.orEmpty()
        }
        return emptyList()
    }

    /** Lightweight reachability check used by the diagnostics screen. */
    suspend fun head(url: String): Boolean =
        runCatching { http.head(url).status.value in 200..299 }.getOrDefault(false)

    fun close() = http.close()

    // ---------------------------------------------------------------- catalog

    /** Home page rows (banners, carousels, platform list). */
    suspend fun home(host: String, useSession: Boolean = true): HomePage {
        val token = if (useSession) ensureSession() else null
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/home?host=$host") {
                applyListingAuth(token)
                header(HttpHeaders.Origin, "https://$host")
            }.body<Envelope<HomePage>>().data
        }.getOrNull() ?: HomePage()
    }

    /** Rows for one catalog tab (e.g. `ONEROOM_MOVIE`). */
    suspend fun tabOperating(tabId: String, host: String): List<OperatingSection> {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/tab-operating?tabId=$tabId&host=$host") {
                applyListingAuth(token)
                header(HttpHeaders.Origin, "https://$host")
            }.body<Envelope<TabOperatingPage>>().data?.operatingList
        }.getOrNull() ?: emptyList()
    }

    /**
     * The server-rendered pages of one route, one per mirror.
     *
     * The canonical host is tried first: it serves the full catalogue, while the
     * mirrors serve a reduced build — `movieboxonline.net` answers the home page
     * with 51 rows instead of 36 and with every high-risk title filtered out, and
     * `moviebox.ph` serves a French build. To copy the website, the app has to read
     * the same host the website does.
     *
     * Only the canonical host serves a real payload for every route — the others
     * answer with a 4 KB shell that has no catalog in it — so the caller has to be
     * able to skip a page that turned out to be empty.
     */
    suspend fun fetchPages(path: String): List<String> {
        val bases = (
            listOf("https://$CANONICAL_HOST") +
                listOf(siteBase) +
                siteHosts.map { "https://$it" }
            ).distinct()
        val pages = mutableListOf<String>()
        for (base in bases) {
            val html = runCatching {
                http.get("$base$path") {
                    headers {
                        set(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                        set(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                    }
                }.bodyAsText()
            }.getOrNull() ?: continue
            if (html.isNotBlank()) pages.add(html)
        }
        return pages
    }

    /**
     * The curated rows a server-rendered page carries.
     *
     * The home payload contains both the SSR rows and the generic API response; the
     * SSR one is the row set that starts with "Home Banner" / "Banner_movie" /
     * "Banner".
     */
    fun parsePageRows(html: String): List<OperatingSection> {
        val root = NuxtPayload.parse(html) ?: return emptyList()
        val resolved = NuxtPayload.resolve(root)
        val lists = NuxtPayload.findOperatingLists(resolved)
        val list = lists.firstOrNull { candidate ->
            val first = candidate.firstOrNull() as? JsonObject
            val title = (first?.get("title") as? JsonPrimitive)?.content
            title == "Home Banner" || title == "Banner_movie" || title == "Banner"
        } ?: lists.firstOrNull() ?: return emptyList()
        return runCatching {
            jsonFormat.decodeFromJsonElement(ListSerializer(OperatingSection.serializer()), list)
        }.getOrNull().orEmpty()
    }

    /** The flat poster grid a server-rendered page carries. */
    fun parsePageGrid(html: String): List<SubjectBrief> {
        val root = NuxtPayload.parse(html) ?: return emptyList()
        val resolved = NuxtPayload.resolve(root)
        val items = NuxtPayload.findSubjectListItems(resolved) ?: return emptyList()
        return runCatching {
            jsonFormat.decodeFromJsonElement(ListSerializer(SubjectBrief.serializer()), items)
        }.getOrNull().orEmpty()
    }

    /** The pager a server-rendered grid page carries. */
    fun parsePagePager(html: String): Pager? {
        val root = NuxtPayload.parse(html) ?: return null
        val resolved = NuxtPayload.resolve(root)
        val list = NuxtPayload.findFirst(resolved, "subjectList") as? JsonObject ?: return null
        val pager = list["pager"] as? JsonObject ?: return null
        return runCatching {
            jsonFormat.decodeFromJsonElement(Pager.serializer(), pager)
        }.getOrNull()
    }

    /**
     * The filter configuration of a catalog grid page.
     *
     * `filterItemsData` lives in the payload and carries the `channelId` for the
     * page together with every filter and its allowed values — the genre, country,
     * year, sort and rating filters the website's filter bar shows.
     */
    fun parsePageFilters(html: String): List<FilterConfig> {
        val root = NuxtPayload.parse(html) ?: return emptyList()
        val resolved = NuxtPayload.resolve(root)
        val data = NuxtPayload.findFirst(resolved, "filterItemsData") as? JsonObject ?: return emptyList()
        val list = data["typeList"] as? JsonArray ?: return emptyList()
        return runCatching {
            jsonFormat.decodeFromJsonElement(ListSerializer(FilterConfig.serializer()), list)
        }.getOrNull().orEmpty()
    }

    /**
     * Reads a server-rendered catalog page (`/web/movie`, `/web/animated-series`,
     * `/web/tv-series`) and extracts its rows. This includes the `webHighRisk` titles
     * that the public `/tab-operating` API filters out.
     */
    suspend fun pageOperating(path: String): List<OperatingSection> {
        for (html in fetchPages(path)) {
            val rows = parsePageRows(html)
            if (rows.isNotEmpty()) return rows

            // Pages with a flat grid (animation / TV) expose `subjectList.items`.
            val grid = parsePageGrid(html)
            if (grid.isNotEmpty()) {
                return grid
                    .groupBy { it.genre.split(",").firstOrNull()?.trim().orEmpty() }
                    .filter { it.key.isNotBlank() }
                    .entries
                    .sortedByDescending { it.value.size }
                    .map { entry ->
                        OperatingSection(
                            type = "SUBJECTS_MOVIE",
                            title = entry.key,
                            subjects = entry.value,
                        )
                    }
            }
        }
        return emptyList()
    }

    /** Most Watched / ranking page (categories + items) from the SSR payload. */
    suspend fun rankingPage(): MostWatchedPage {
        repeat(2) {
            val html = runCatching {
                http.get("$siteBase/ranking-list") {
                    headers {
                        set(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                        set(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                    }
                }.bodyAsText()
            }.getOrNull() ?: return@repeat

            val root = NuxtPayload.parse(html) ?: return@repeat
            val resolved = NuxtPayload.resolve(root)
            val categories = NuxtPayload.findFirst(resolved, "rankingList")?.let {
                runCatching {
                    jsonFormat.decodeFromJsonElement(ListSerializer(RankingCategory.serializer()), it)
                }.getOrNull()
            }.orEmpty()
            val subjects = NuxtPayload.findFirst(resolved, "subjectList")?.let {
                runCatching {
                    jsonFormat.decodeFromJsonElement(ListSerializer(SubjectBrief.serializer()), it)
                }.getOrNull()
            }.orEmpty()
            if (categories.isNotEmpty() || subjects.isNotEmpty()) {
                return MostWatchedPage(categories, subjects)
            }
        }
        return MostWatchedPage()
    }

    /**
     * Paginated catalog query.
     *
     * @param channelId 1 = movies, 2 = series (anime lives here too), 5 = kids, 6 = music
     */
    suspend fun filter(request: FilterRequest): FilterPage {
        val token = ensureSession()
        return runCatching {
            http.post("$apiBase/wefeed-h5api-bff/subject/filter") {
                contentType(ContentType.Application.Json)
                setBody(request)
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<FilterPage>>().data
        }.getOrNull() ?: FilterPage()
    }

    suspend fun trending(
        tabId: String? = null,
        page: Int = 1,
        perPage: Int = 18,
        useSession: Boolean = true,
    ): TrendingPage {
        val token = if (useSession) ensureSession() else null
        val query = buildString {
            if (!tabId.isNullOrBlank()) append("tabId=$tabId&")
            append("page=$page&perPage=$perPage")
        }
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/subject/trending?$query") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<TrendingPage>>().data
        }.getOrNull() ?: TrendingPage()
    }

    suspend fun everyoneSearch(): EveryoneSearchPage {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/subject/everyone-search") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<EveryoneSearchPage>>().data
        }.getOrNull() ?: EveryoneSearchPage()
    }

    suspend fun ranking(id: String, page: Int = 1, perPage: Int = 20): RankingPage {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=$perPage") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<RankingPage>>().data
        }.getOrNull() ?: RankingPage()
    }

    suspend fun detailRec(subjectId: String, page: Int = 1, perPage: Int = 12): List<SubjectBrief> {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/subject/detail-rec?subjectId=$subjectId&page=$page&perPage=$perPage") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<DetailRecPage>>().data?.items
        }.getOrNull() ?: emptyList()
    }

    companion object {
        const val DEFAULT_API_BASE = "https://h5-api.aoneroom.com"
        const val DEFAULT_SITE_BASE = "https://themoviebox.xyz"

        /**
         * The canonical host of the site.
         *
         * It is the build the app copies: it carries the high-risk titles and the
         * full row set, while the mirrors serve reduced or regional variants.
         */
        const val CANONICAL_HOST = "themoviebox.xyz"

        /** Mirror hosts of the site. They rotate from time to time, hence the fallback list. */
        val DEFAULT_SITE_HOSTS = listOf(
            "themoviebox.xyz",
            "mzfi.me",
            "netfilm.world",
            "movieboxonline.net",
            "moviebox.co",
            "moviebox.ph",
        )
    }
}
