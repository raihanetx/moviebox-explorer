package com.example.movieboxexplorer.data

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

suspend fun MovieBoxApi.play(
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
fun MovieBoxApi.normalizePlayData(data: PlayData): PlayData {
        if (data.streams.isNotEmpty()) return data
        val streams = parseResourceStreams(data.resource)
        return if (streams.isEmpty()) data else data.copy(streams = streams)
    }

fun MovieBoxApi.parseResourceStreams(resource: JsonElement?): List<PlayStream> {
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
suspend fun MovieBoxApi.caption(
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
suspend fun MovieBoxApi.head(url: String): Boolean =
        runCatching { http.head(url).status.value in 200..299 }.getOrDefault(false)

fun MovieBoxApi.close() = http.close()


