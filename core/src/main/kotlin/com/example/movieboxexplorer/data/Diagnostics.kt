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