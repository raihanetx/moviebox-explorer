package com.example.movieboxexplorer.data

import kotlinx.coroutines.delay

/**
 * Resolves a playable source with retries for transient failures.
 *
 * A download (or retry/resume) resolves a signed CDN URL and then asks the
 * system to fetch it. Between those two moments the network can blink — a VPN
 * toggling, a DNS change (Cloudflare 1.1.1.1), a momentary reconnect. A single
 * attempt turns that into `PlaybackResult.Failed`; this retries **fresh
 * resolution** with short backoff before giving up.
 *
 * Only transient failures are retried. A definitive answer is returned
 * immediately: [PlaybackResult.Ready], [PlaybackResult.NoResource] (the
 * episode was never uploaded) and [PlaybackResult.Locked] (VIP/premium).
 */
object PlaybackRetry {

    suspend fun resolveFresh(
        attempts: Int = 3,
        backoffMillis: (attempt: Int) -> Long = { 500L * (it + 1) },
        resolve: suspend () -> PlaybackResult,
    ): PlaybackResult {
        var last: PlaybackResult = PlaybackResult.Failed("Could not reach the catalogue")
        val total = attempts.coerceAtLeast(1)
        repeat(total) { attempt ->
            val result = runCatching { resolve() }.getOrNull()
            if (result is PlaybackResult.Ready) return result
            last = result ?: PlaybackResult.Failed("Could not reach the catalogue")
            // A definitive answer isn't retried.
            if (result is PlaybackResult.NoResource || result is PlaybackResult.Locked) return last
            if (attempt < total - 1) delay(backoffMillis(attempt))
        }
        return last
    }
}
