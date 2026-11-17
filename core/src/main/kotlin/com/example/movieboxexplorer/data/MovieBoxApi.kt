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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    internal val apiBase: String = DEFAULT_API_BASE,
    internal val siteHosts: List<String> = DEFAULT_SITE_HOSTS,
    internal val siteBase: String = DEFAULT_SITE_BASE,
) {

    internal val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = false
    }

    internal val http = HttpClient(CIO) {
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


    internal fun clientInfo(): String = """{"timezone":"${ZoneId.systemDefault().id}"}"""

    internal fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** `"<unix seconds>,<md5 of the reversed seconds>"` — matches the web client's scheme. */
    internal fun clientToken(): String {
        val seconds = System.currentTimeMillis() / 1000
        return "$seconds,${md5Hex(seconds.toString().reversed())}"
    }

    internal fun io.ktor.client.request.HttpRequestBuilder.applyAuth(token: String?) {
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
    internal fun io.ktor.client.request.HttpRequestBuilder.applyListingAuth(token: String?) {
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
    fun close() = http.close()

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
