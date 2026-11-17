package com.example.movieboxexplorer.data
import com.example.movieboxexplorer.data.MovieBoxApi.Companion.CANONICAL_HOST


import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.setBody
import io.ktor.http.headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

suspend fun MovieBoxApi.home(host: String, useSession: Boolean = true): HomePage {
        val token = if (useSession) ensureSession() else null
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/home?host=$host") {
                applyListingAuth(token)
                header(HttpHeaders.Origin, "https://$host")
            }.body<Envelope<HomePage>>().data
        }.getOrNull() ?: HomePage()
    }

    /** Rows for one catalog tab (e.g. `ONEROOM_MOVIE`). */
suspend fun MovieBoxApi.tabOperating(tabId: String, host: String): List<OperatingSection> {
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
suspend fun MovieBoxApi.fetchPages(path: String): List<String> =
        coroutineScope {
            val bases = (
                listOf("https://$CANONICAL_HOST") +
                    listOf(siteBase) +
                    siteHosts.map { "https://$it" }
            ).distinct()
            val pages = java.util.concurrent.ConcurrentHashMap<String, String>()
            val jobs = bases.map { base ->
                async {
                    runCatching {
                        http.get("$base$path") {
                            headers {
                                set(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                                set(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                            }
                        }.bodyAsText()
                    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { pages[base] = it }
                }
            }
            jobs.forEach { it.await() }
            bases.mapNotNull { pages[it] }
        }

    /**
     * The curated rows a server-rendered page carries.
     *
     * The home payload contains both the SSR rows and the generic API response; the
     * SSR one is the row set that starts with "Home Banner" / "Banner_movie" /
     * "Banner".
     */
fun MovieBoxApi.parsePageRows(html: String): List<OperatingSection> {
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
fun MovieBoxApi.parsePageGrid(html: String): List<SubjectBrief> {
        val root = NuxtPayload.parse(html) ?: return emptyList()
        val resolved = NuxtPayload.resolve(root)
        val items = NuxtPayload.findSubjectListItems(resolved) ?: return emptyList()
        return runCatching {
            jsonFormat.decodeFromJsonElement(ListSerializer(SubjectBrief.serializer()), items)
        }.getOrNull().orEmpty()
    }

    /** The pager a server-rendered grid page carries. */
fun MovieBoxApi.parsePagePager(html: String): Pager? {
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
fun MovieBoxApi.parsePageFilters(html: String): List<FilterConfig> {
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
suspend fun MovieBoxApi.pageOperating(path: String): List<OperatingSection> {
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
suspend fun MovieBoxApi.rankingPage(): MostWatchedPage {
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
suspend fun MovieBoxApi.filter(request: FilterRequest): FilterPage {
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

suspend fun MovieBoxApi.trending(
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

suspend fun MovieBoxApi.everyoneSearch(): EveryoneSearchPage {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/subject/everyone-search") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<EveryoneSearchPage>>().data
        }.getOrNull() ?: EveryoneSearchPage()
    }

suspend fun MovieBoxApi.ranking(id: String, page: Int = 1, perPage: Int = 20): RankingPage {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=$perPage") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<RankingPage>>().data
        }.getOrNull() ?: RankingPage()
    }

suspend fun MovieBoxApi.detailRec(subjectId: String, page: Int = 1, perPage: Int = 12): List<SubjectBrief> {
        val token = ensureSession()
        return runCatching {
            http.get("$apiBase/wefeed-h5api-bff/subject/detail-rec?subjectId=$subjectId&page=$page&perPage=$perPage") {
                applyListingAuth(token)
                siteHosts.firstOrNull()?.let { header(HttpHeaders.Origin, "https://$it") }
            }.body<Envelope<DetailRecPage>>().data?.items
        }.getOrNull() ?: emptyList()
    }


