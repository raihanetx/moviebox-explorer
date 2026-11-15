package com.example.movieboxexplorer.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Client for the MovieBox **mobile** BFF (`/wefeed-mobile-bff`).
 *
 * The mobile API is the catalog side of the dual-API architecture: it carries the full
 * metadata, every language track and the per-season episode counts. Playback is served
 * by the H5 API ([MovieBoxApi.play]), because the mobile `play-info` endpoint only
 * hands out promo renditions.
 *
 * Every request is signed with HMAC-MD5. The signature covers the complete URL —
 * including the sorted query — so the URL is built before the signature is computed.
 */
class MobileApi(
    private val bases: List<String> = DEFAULT_BASES,
    private val deviceId: String = randomHex(32),
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
        install(HttpTimeout) { requestTimeoutMillis = 8_000 }
    }

    /** One session token per host; the bootstrap token is host-scoped. */
    private val sessions = LinkedHashMap<String, String>()

    /** Last HTTP status/body, for diagnostics (the client itself never throws). */
    @Volatile
    var lastStatus: Int? = null
        private set

    @Volatile
    var lastBody: String? = null
        private set

    /** Requests an anonymous session and caches the token for [base]. */
    suspend fun session(base: String = bases.first()): String? {
        sessions[base]?.let { return it }
        val url = buildUrl(base, BOOTSTRAP_PATH, BOOTSTRAP_QUERY)
        val response = runCatching {
            http.get(url) { applyMobileHeaders(HttpMethod.Get, url, body = null, token = null) }
        }.getOrNull() ?: return null
        lastStatus = response.status.value
        lastBody = response.bodyAsText().take(600)
        captureToken(base, response.headers["x-user"])
        return sessions[base]
    }

    // ---------------------------------------------------------------- catalog

    suspend fun search(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): MobileSearchPage {
        val body = jsonFormat.encodeToString(
            MobileSearchRequest.serializer(),
            MobileSearchRequest(keyword = keyword, page = page, perPage = perPage),
        )
        for (base in bases) {
            val text = postJson(base, "$MOBILE_PREFIX/subject-api/search", body) ?: continue
            val envelope = decodeEnvelope(text, MobileSearchPage.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data ?: MobileSearchPage()
        }
        return MobileSearchPage()
    }

    /** [search] with the `subject` wrapper (or a bare subject) unwrapped. */
    suspend fun searchSubjects(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<SubjectBrief> = parseSubjects(search(keyword, page, perPage).items)

    suspend fun searchPage(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): SearchPage {
        val result = search(keyword, page, perPage)
        return SearchPage(pager = result.pager, items = parseSubjects(result.items))
    }

    suspend fun detail(subjectId: String): MobileDetail? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/subject-api/get",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, JsonElement.serializer()) ?: continue
            if (envelope.code != 0) continue
            val data = envelope.data as? JsonObject ?: continue

            // Some builds wrap the record in `data.subject`; others return the
            // subject fields directly under `data`.
            val wrapped = data["subject"] as? JsonObject
            val detail = if (wrapped != null) {
                runCatching {
                    jsonFormat.decodeFromJsonElement(MobileDetail.serializer(), data)
                }.getOrNull()
            } else {
                val subject = runCatching {
                    jsonFormat.decodeFromJsonElement(Subject.serializer(), data)
                }.getOrNull()
                subject
                    ?.takeIf { it.subjectId.isNotBlank() }
                    ?.let {
                        MobileDetail(
                            subject = it.copy(
                                detailPath = it.detailPath.ifBlank {
                                    it.detailUrl.trimEnd('/').substringAfterLast('/')
                                },
                            ),
                        )
                    }
            }
            if (detail?.subject != null) return detail
        }
        return null
    }

    suspend fun seasonInfo(subjectId: String): Resource? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/subject-api/season-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileSeasonInfo.serializer()) ?: continue
            if (envelope.code == 0) {
                val resource = envelope.data?.toResource()
                if (resource != null && resource.seasons.isNotEmpty()) return resource
            }
        }
        return null
    }

    suspend fun dubInfo(subjectId: String): List<MobileDub> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/subject-api/dub-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileDubInfo.serializer()) ?: continue
            if (envelope.code == 0) {
                val dubs = envelope.data?.dubs?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.items
                    ?: emptyList()
                if (dubs.isNotEmpty()) return dubs
            }
        }
        return emptyList()
    }

    suspend fun staffInfo(subjectId: String): List<Staff> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/subject-api/staff-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileStaffPage.serializer()) ?: continue
            if (envelope.code == 0) {
                val staff = envelope.data?.stars?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.items
                    ?: emptyList()
                if (staff.isNotEmpty()) return staff
            }
        }
        return emptyList()
    }

    suspend fun recommend(
        subjectId: String,
        page: Int = 1,
        perPage: Int = 12,
    ): List<SubjectBrief> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            for (path in RECOMMEND_PATHS) {
                val text = getJson(
                    base,
                    path,
                    mapOf(
                        "subjectId" to subjectId,
                        "page" to page.toString(),
                        "perPage" to perPage.toString(),
                    ),
                ) ?: continue
                val envelope = decodeEnvelope(text, MobileRecommendPage.serializer()) ?: continue
                if (envelope.code == 0) {
                    val subjects = parseSubjects(envelope.data?.items.orEmpty())
                    if (subjects.isNotEmpty()) return subjects
                }
            }
        }
        return emptyList()
    }

    suspend fun rankingList(
        categoryId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): MobileRankingPage {
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/tab/ranking-list",
                mapOf(
                    "tabId" to "0",
                    "categoryType" to categoryId,
                    "page" to page.toString(),
                    "perPage" to perPage.toString(),
                ),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileRankingPage.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data ?: MobileRankingPage()
        }
        return MobileRankingPage()
    }

    suspend fun rankingSubjects(
        categoryId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<SubjectBrief> {
        val page = rankingList(categoryId, page, perPage)
        return parseSubjects(page.items.ifEmpty { page.subjectList })
    }

    suspend fun comments(
        subjectId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<MobileComment> {
        if (subjectId.isBlank()) return emptyList()
        val body = jsonFormat.encodeToString(
            MobileCommentRequest.serializer(),
            MobileCommentRequest(subjectId, page, perPage),
        )
        for (base in bases) {
            val text = postJson(base, "$MOBILE_PREFIX/subject-api/comment/list", body) ?: continue
            val envelope = decodeEnvelope(text, MobileCommentPage.serializer()) ?: continue
            if (envelope.code == 0) {
                val comments = envelope.data?.items?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.list
                    ?: emptyList()
                if (comments.isNotEmpty()) return comments
            }
        }
        return emptyList()
    }

    /**
     * The mobile play endpoint. The guide reports that it only returns promo
     * renditions, so this is exposed for diagnostics rather than used for playback.
     */
    suspend fun playInfo(
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
        isVip: Boolean = false,
    ): PlayData? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MOBILE_PREFIX/subject-api/play-info",
                mapOf(
                    "subjectId" to subjectId,
                    "se" to season.toString(),
                    "ep" to episode.toString(),
                    "isVip" to isVip.toString(),
                ),
            ) ?: continue
            val envelope = decodeEnvelope(text, PlayData.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data
        }
        return null
    }

    fun close() = http.close()

    // ---------------------------------------------------------------- internals

    private suspend fun getJson(
        base: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String? {
        val url = buildUrl(base, path, query)
        val token = session(base)
        val response = runCatching {
            http.get(url) {
                applyMobileHeaders(HttpMethod.Get, url, body = null, token = token)
            }
        }.getOrNull() ?: return null
        lastStatus = response.status.value
        val text = response.bodyAsText()
        lastBody = text.take(600)
        captureToken(base, response.headers["x-user"])
        return text.takeIf { it.isNotBlank() }
    }

    private suspend fun postJson(base: String, path: String, body: String): String? {
        val url = buildUrl(base, path)
        val token = session(base)
        val response = runCatching {
            http.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                applyMobileHeaders(HttpMethod.Post, url, body = body, token = token)
            }
        }.getOrNull() ?: return null
        lastStatus = response.status.value
        val text = response.bodyAsText()
        lastBody = text.take(600)
        captureToken(base, response.headers["x-user"])
        return text.takeIf { it.isNotBlank() }
    }

    private fun HttpRequestBuilder.applyMobileHeaders(
        method: HttpMethod,
        url: String,
        body: String?,
        token: String?,
    ) {
        val timestamp = System.currentTimeMillis()
        header("user-agent", USER_AGENT)
        header("accept", "application/json")
        header("content-type", "application/json")
        header("x-client-token", MobileSigner.clientToken())
        header("x-tr-signature", MobileSigner.signature(method.value, url, body, timestamp))
        header("x-client-info", clientInfo())
        header("x-client-status", "0")
        header("x-client-build", APP_VERSION_NAME)
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private fun captureToken(base: String, raw: String?) {
        val token = parseToken(raw) ?: return
        sessions[base] = token
    }

    private fun parseToken(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("{")) {
            return runCatching {
                jsonFormat.parseToJsonElement(value)
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        // A bare JWT has two dots.
        return value.takeIf { it.count { ch -> ch == '.' } >= 2 }
    }

    private inline fun <reified T> decodeEnvelope(
        text: String,
        serializer: KSerializer<T>,
    ): Envelope<T>? = runCatching {
        jsonFormat.decodeFromString(Envelope.serializer(serializer), text)
    }.getOrNull()

    private fun parseSubjects(items: List<JsonElement>): List<SubjectBrief> =
        items.mapNotNull { item ->
            val objectItem = item as? JsonObject ?: return@mapNotNull null
            val subject = objectItem["subject"] as? JsonObject ?: objectItem
            runCatching {
                jsonFormat.decodeFromJsonElement(SubjectBrief.serializer(), subject)
            }.getOrNull()?.takeIf { it.subjectId.isNotBlank() }
        }

    private fun clientInfo(): String {
        val timezone = runCatching { ZoneId.systemDefault().id }.getOrDefault("UTC")
        val locale = Locale.getDefault()
        val region = locale.country.ifBlank { "IN" }
        val language = locale.language.ifBlank { "en" }
        return buildString {
            append("{")
            append("\"package_name\":\"").append(PACKAGE_NAME).append("\",")
            append("\"version_name\":\"").append(APP_VERSION_NAME).append("\",")
            append("\"version_code\":").append(APP_VERSION_CODE).append(",")
            append("\"os\":\"android\",")
            append("\"os_version\":\"").append(ANDROID_VERSION).append("\",")
            append("\"device_id\":\"").append(deviceId).append("\",")
            append("\"install_store\":\"official\",")
            append("\"gaid\":\"").append(UUID.randomUUID().toString()).append("\",")
            append("\"brand\":\"Google\",")
            append("\"model\":\"Pixel 8\",")
            append("\"system_language\":\"").append(language).append("\",")
            append("\"net\":\"NETWORK_WIFI\",")
            append("\"region\":\"").append(region).append("\",")
            append("\"timezone\":\"").append(timezone).append("\",")
            append("\"sp_code\":\"\"")
            append("}")
        }
    }

    private fun buildUrl(base: String, path: String, query: Map<String, String> = emptyMap()): String {
        val root = base.trimEnd('/')
        if (query.isEmpty()) return root + path
        val encoded = query.entries.joinToString("&") { (key, value) -> "$key=$value" }
        return "$root$path?$encoded"
    }

    companion object {
        const val DEFAULT_BASE = "https://apig.inmoviebox.com"
        const val ALT_BASE = "https://api.aoneroom.com"
        val DEFAULT_BASES: List<String> = listOf(DEFAULT_BASE, ALT_BASE)

        const val PACKAGE_NAME = "com.community.mbox.in"
        const val APP_VERSION_NAME = "4.0.03.0920.03"
        const val APP_VERSION_CODE = 50_020_130
        const val ANDROID_VERSION = "14"
        const val USER_AGENT =
            "com.community.mbox.in/50020130 (Linux; U; Android 14; en_IN; Pixel 8; " +
                "Build/UD1A.230803.041; Cronet/145.0.7582.0)"

        private const val MOBILE_PREFIX = "/wefeed-mobile-bff"
        private const val BOOTSTRAP_PATH = "$MOBILE_PREFIX/tab/ranking-list"
        private val BOOTSTRAP_QUERY = mapOf(
            "tabId" to "0",
            "categoryType" to "4516404531735022304",
            "page" to "1",
            "perPage" to "1",
        )

        /** The guide names one path; some builds expose the list under another. */
        private val RECOMMEND_PATHS = listOf(
            "$MOBILE_PREFIX/subject-api/recommend",
            "$MOBILE_PREFIX/subject-api/recommend/list",
            "$MOBILE_PREFIX/subject-api/recommend-rec",
        )

        private fun randomHex(length: Int): String {
            val bytes = ByteArray((length + 1) / 2)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
        }
    }
}

/** Normalises the mobile `season-info` quality labels into [Resource] seasons. */
private fun MobileSeasonInfo.toResource(): Resource {
    val seasons = seasons.map { season ->
        val labels = season.resolutions.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.toIntOrNull()
                is JsonObject -> (element["resolution"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.toIntOrNull()
                else -> null
            }
        }
        val resolutions = if (labels.isNotEmpty()) {
            labels.map { Resolution(resolution = it, epNum = 0) }
        } else {
            season.resolutions.mapNotNull { element ->
                (element as? JsonObject)?.let { objectElement ->
                    val resolution = (objectElement["resolution"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.toIntOrNull()
                        ?: return@mapNotNull null
                    val epNum = (objectElement["epNum"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.toIntOrNull()
                        ?: 0
                    Resolution(resolution, epNum)
                }
            }
        }
        val maxEp = season.maxEp.takeIf { it > 0 }
            ?: season.allEp.split(",").count { it.isNotBlank() }
        Season(
            se = season.se,
            maxEp = maxEp,
            allEp = season.allEp,
            resolutions = resolutions,
        )
    }
    return Resource(seasons = seasons)
}