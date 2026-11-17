package com.example.movieboxexplorer.data

import java.time.Instant

/** Result of one diagnostics check. */
enum class CheckStatus { PASS, FAIL, WARN, SKIP }

data class CheckResult(
    val name: String,
    val status: CheckStatus,
    val durationMs: Long,
    val detail: String,
)

/**
 * A full diagnostics report.
 *
 * The report is plain text so it can be copied from the app and pasted into an issue
 * or a chat. It never contains tokens or secrets.
 */
data class DiagnosticsReport(
    val appVersion: String,
    val apiBase: String,
    val siteHost: String,
    val cacheBytes: Long,
    val results: List<CheckResult>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val passed: Int get() = results.count { it.status == CheckStatus.PASS }
    val warned: Int get() = results.count { it.status == CheckStatus.WARN }
    val failed: Int get() = results.count { it.status == CheckStatus.FAIL }

    fun text(): String = buildString {
        appendLine("MovieBox Diagnostics")
        appendLine("Time: ${Instant.ofEpochMilli(timestamp)}")
        appendLine("App: $appVersion | API: $apiBase | Host: $siteHost | Cache: ${cacheBytes / 1024} KB")
        appendLine("=".repeat(64))
        results.forEach { result ->
            appendLine(
                "${result.status.name.padEnd(4)} " +
                    "${result.name.padEnd(20)} " +
                    "${result.durationMs}ms  ${result.detail}",
            )
        }
        appendLine("=".repeat(64))
        appendLine("Summary: $passed passed, $warned warning(s), $failed failed")
    }
}

// ------------------------------------------------------------- checks

suspend fun MovieBoxRepository.diagnostics(
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
        val token = this.api.ensureSession()
        (if (token.isNotBlank()) CheckStatus.PASS else CheckStatus.FAIL) to
            "token ${token.length} chars"
    }

    val home = this.api.home(host)
    check("H5 home") {
        (if (home.operatingList.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
            "${home.operatingList.size} sections, ${home.platformList.size} platforms"
    }

    check("H5 search") {
        val search = this.api.search("Black Clover", page = 1, perPage = 3, subjectType = 2)
        val ranked = SearchRank.rank(search.items, "Black Clover")
        val first = ranked.firstOrNull()
        (if (first != null && first.title.contains("Black Clover", true)) CheckStatus.PASS else CheckStatus.WARN) to
            "${search.items.size} results, first=${first?.title.orEmpty()}"
    }

    check("Mobile session") {
        val token = this.mobile.session()
        (if (!token.isNullOrBlank()) CheckStatus.PASS else CheckStatus.FAIL) to
            "token ${token?.length ?: 0} chars"
    }

    val mobileItems = SearchRank.rank(
        this.mobile.searchSubjects("Black Clover", perPage = 5),
        "Black Clover",
    )
    check("Mobile search") {
        (if (mobileItems.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
            "${mobileItems.size} results, first=${mobileItems.firstOrNull()?.title.orEmpty()}"
    }

    val mobileFirst = mobileItems.firstOrNull()
    val mobileDetail = mobileFirst?.let { this.mobile.detail(it.subjectId) }
    check("Mobile detail") {
        val subject = mobileDetail?.subject
        (if (subject != null) CheckStatus.PASS else CheckStatus.FAIL) to
            "${subject?.title.orEmpty()}, dubs=${mobileDetail?.dubs?.size ?: 0}"
    }

    check("Mobile dubs") {
        val dubs = mobileFirst?.let { this.mobile.dubInfo(it.subjectId) }.orEmpty()
        (if (dubs.isNotEmpty()) CheckStatus.PASS else CheckStatus.WARN) to
            dubs.joinToString(", ") { it.lanName }
    }

    check("Mobile seasons") {
        val resource = mobileFirst?.let { this.mobile.seasonInfo(it.subjectId) }
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

    var detailInfo: TitleInfo? = null
    check("Detail path") {
        val info = loadTitle(detailPath, detailId)
        detailInfo = info
        (if (info != null && info.languages.isNotEmpty()) CheckStatus.PASS else CheckStatus.FAIL) to
            "${info?.languages?.size ?: 0} languages, ${info?.referenceEpisodes ?: 0} episodes"
    }

    check("Language seasons") {
        val info = detailInfo
        if (info == null) {
            CheckStatus.FAIL to "no detail"
        } else {
            val text = info.languages.take(8).joinToString(" | ") { language ->
                "${language.label}: " +
                    language.seasons.joinToString(",") { "S${it.season}:${it.episodes}" }
            }
            CheckStatus.PASS to text
        }
    }

    check("Related") {
        val items = this.api.detailRec(detailId, perPage = 5)
        (if (items.isNotEmpty()) CheckStatus.PASS else CheckStatus.WARN) to
            "${items.size} related items"
    }

    check("Playback") {
        val data = this.api.play(detailPath, detailId, 1, 1)
        if (data == null) {
            CheckStatus.FAIL to "no response"
        } else {
            val source = selectPlaybackSource(
                data,
                mapOf(
                    "Referer" to api.cdnReferer(),
                    "User-Agent" to MovieBoxRepository.PLAYBACK_USER_AGENT,
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


