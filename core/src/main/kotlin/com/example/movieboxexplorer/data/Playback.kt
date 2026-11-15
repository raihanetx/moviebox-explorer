package com.example.movieboxexplorer.data

import kotlinx.serialization.Serializable

/**
 * One rendition of an episode.
 *
 * The catalogue hands out two shapes of the same episode:
 *
 *  * progressive **MP4** — one file per quality level, so [resolutions] holds a single
 *    number. Plays everywhere, but only one quality is available.
 *  * a **DASH** manifest — one adaptive stream carrying every quality, so [resolutions]
 *    holds a comma separated ladder.
 */
@Serializable
data class PlayStream(
    val id: String = "",
    val url: String = "",
    val format: String = "",
    val resolutions: String = "",
    val size: String = "",
    val duration: Int = 0,
    val codecName: String = "",
    /**
     * Quality label as the H5 / mobile play endpoints sometimes report it (`"1080"`).
     * [resolution] falls back to this when [resolutions] is empty.
     */
    val quality: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** Codec as the H5 play endpoint reports it (`"h264"`, `"hevc"`). */
    val codec: String = "",
    val bitrate: Int = 0,
    /**
     * Edge cache credential. Only DASH manifests carry one; the value has to be sent
     * verbatim in the header named by [signHeaderKey].
     */
    val signCookie: String = "",
    /** Name of the header [signCookie] must be sent in (`X-MB-Token`). */
    val signHeaderKey: String = "",
    val vipLocked: Boolean = false,
) {
    /** Highest quality this rendition offers. */
    val resolution: Int
        get() = resolutions.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .maxOrNull()
            ?: quality.trim().removeSuffix("p").toIntOrNull()
            ?: height.takeIf { it > 0 }
            ?: 0

    val effectiveCodec: String
        get() = codecName.ifBlank { codec }

    /**
     * Whether this is one of the platform's promo placeholders.
     *
     * Real streams live on the signed CDN hosts (`bcdnxw.hakunaymatata.com`,
     * `/bt/`, `/resource/`, …) and carry `sign`/`t` parameters. The promo clip is
     * served from `macdn.aoneroom.com/other/…` with no signature.
     */
    val isPromo: Boolean
        get() = url.contains("macdn.aoneroom.com/other/", ignoreCase = true) ||
            (url.contains("macdn.aoneroom.com", ignoreCase = true) && !url.contains("sign=", ignoreCase = true))

    /** The credential is only useful when it is actually populated. */
    val hasCredential: Boolean
        get() = signCookie.isNotBlank() && signHeaderKey.isNotBlank()
}

/**
 * Access rules the platform attaches to a title — VIP gating, free-episode allowances
 * and preview windows.
 */
@Serializable
data class AccessStrategy(
    val ruleType: Int = 0,
    val requiredVipLevel: Int = 0,
    val freeEpisodeCount: Int = 0,
    val previewSeconds: Int = 0,
)

/** Playback limits for an anonymous session. */
@Serializable
data class PlayConfig(
    val adFree: Boolean = false,
    val maxResolution: Int = 0,
)

/** The body of `GET /subject/play`. */
@Serializable
data class PlayData(
    val streams: List<PlayStream> = emptyList(),
    val dash: List<PlayStream> = emptyList(),
    val hls: List<PlayStream> = emptyList(),
    val hasResource: Boolean = false,
    val vipLocked: Boolean = false,
    val limited: Boolean = false,
    val limitedCode: String = "",
    val freeNum: Int = 0,
    val playConfig: PlayConfig = PlayConfig(),
    val accessStrategy: AccessStrategy? = null,
)

/**
 * A resolved episode: where to fetch it from and which headers the CDN demands.
 *
 * The CDN rejects requests that do not carry a `Referer` pointing at one of the site's
 * mirror hosts — without it the CDN answers `429`. DASH manifests additionally need the
 * edge cache credential in the header named by the source.
 */
data class PlaybackSource(
    val url: String,
    val format: String,
    val resolution: Int,
    val codec: String,
    val durationSeconds: Int,
    val headers: Map<String, String> = emptyMap(),
) {
    val isAdaptive: Boolean get() = format.equals("DASH", ignoreCase = true) ||
        format.equals("HLS", ignoreCase = true)
}

/** Outcome of resolving one episode. */
sealed interface PlaybackResult {
    data class Ready(val source: PlaybackSource) : PlaybackResult

    /** The episode exists in the catalogue but was never uploaded. */
    data object NoResource : PlaybackResult

    /** The episode is behind the platform's access rules (VIP level, preview window). */
    data class Locked(val message: String) : PlaybackResult

    /** The request itself failed. */
    data class Failed(val message: String) : PlaybackResult
}

/**
 * Why the platform refuses to hand out this episode, or `null` when it does.
 *
 * The play response is the authority here — the *detail* record carries an
 * `accessStrategy` that says a track needs VIP, while the play response for the same
 * track reports `vipLocked = false` and hands over the streams.
 */
fun PlayData.lockReason(): String? {
    if (vipLocked) return "This episode needs MovieBox Premium"
    val strategy = accessStrategy
    if (strategy != null && strategy.requiredVipLevel > 0 && strategy.freeEpisodeCount == 0) {
        return "This episode needs MovieBox Premium"
    }
    return null
}

/**
 * Picks the rendition to play.
 *
 * Progressive MP4 is preferred: the CDN accepts it with nothing but a `Referer`
 * pointing at one of the site's mirror hosts, while the adaptive DASH manifest
 * additionally needs the edge cache credential. The adaptive manifest is only used
 * for titles that ship no MP4 at all.
 */
fun selectPlaybackSource(data: PlayData, headers: Map<String, String>): PlaybackSource? {
    val progressive = data.streams
        .filter { it.url.isNotBlank() && !it.vipLocked && !it.isPromo }
        .maxByOrNull { it.resolution }
    if (progressive != null) {
        return PlaybackSource(
            url = progressive.url,
            format = progressive.format.ifBlank { "MP4" },
            resolution = progressive.resolution,
            codec = progressive.effectiveCodec,
            durationSeconds = progressive.duration,
            headers = headers,
        )
    }

    val adaptive = (data.dash + data.hls)
        .filter { it.url.isNotBlank() && !it.vipLocked && !it.isPromo }
        .maxByOrNull { it.resolution }
        ?: return null

    val adaptiveHeaders = buildMap {
        putAll(headers)
        if (adaptive.hasCredential) {
            // The edge cache credential is only accepted verbatim in the header the
            // source names — `X-MB-Token` in practice. The `sign` part on its own
            // is rejected with 403.
            put(adaptive.signHeaderKey, adaptive.signCookie)
        }
    }
    return PlaybackSource(
        url = adaptive.url,
        format = adaptive.format.ifBlank { "DASH" },
        resolution = adaptive.resolution,
        codec = adaptive.effectiveCodec,
        durationSeconds = adaptive.duration,
        headers = adaptiveHeaders,
    )
}
