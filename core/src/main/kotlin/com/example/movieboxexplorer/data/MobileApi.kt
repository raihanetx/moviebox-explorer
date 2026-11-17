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
    internal val bases: List<String> = DEFAULT_BASES,
    internal val deviceId: String = randomHex(32),
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
        install(HttpTimeout) { requestTimeoutMillis = 8_000 }
    }

    /** One session token per host; the bootstrap token is host-scoped. */
    internal val sessions = LinkedHashMap<String, String>()

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
        val url = buildUrl(base, MobileConstants.BOOTSTRAP_PATH, MobileConstants.BOOTSTRAP_QUERY)
        val response = runCatching {
            http.get(url) { applyMobileHeaders(HttpMethod.Get, url, body = null, token = null) }
        }.getOrNull() ?: return null
        lastStatus = response.status.value
        lastBody = response.bodyAsText().take(600)
        captureToken(base, response.headers["x-user"])
        return sessions[base]
    }

    // ---------------------------------------------------------------- catalog


    fun close() = http.close()

    // ---------------------------------------------------------------- internals

    internal suspend fun getJson(
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

    internal suspend fun postJson(base: String, path: String, body: String): String? {
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

    internal fun HttpRequestBuilder.applyMobileHeaders(
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

    internal fun captureToken(base: String, raw: String?) {
        val token = parseToken(raw) ?: return
        sessions[base] = token
    }

    internal fun parseToken(raw: String?): String? {
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

    internal inline fun <reified T> decodeEnvelope(
        text: String,
        serializer: KSerializer<T>,
    ): Envelope<T>? = runCatching {
        jsonFormat.decodeFromString(Envelope.serializer(serializer), text)
    }.getOrNull()

    internal fun parseSubjects(items: List<JsonElement>): List<SubjectBrief> =
        items.mapNotNull { item ->
            val objectItem = item as? JsonObject ?: return@mapNotNull null
            val subject = objectItem["subject"] as? JsonObject ?: objectItem
            runCatching {
                jsonFormat.decodeFromJsonElement(SubjectBrief.serializer(), subject)
            }.getOrNull()?.takeIf { it.subjectId.isNotBlank() }
        }

    internal fun clientInfo(): String {
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

    internal fun buildUrl(base: String, path: String, query: Map<String, String> = emptyMap()): String {
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

        internal fun randomHex(length: Int): String {
            val bytes = ByteArray((length + 1) / 2)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
        }
    }
}
