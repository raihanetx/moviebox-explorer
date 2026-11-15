package com.example.movieboxexplorer.data

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The request signer for the MovieBox mobile BFF.
 *
 * Every mobile request carries:
 *
 *  - `x-client-token` — `"<unix seconds>,<md5(reversed seconds)>"`;
 *  - `x-tr-signature` — `"<timestamp ms>|2|<base64(hmac-md5(canonical request))>"`.
 *
 * The canonical request is the method, the JSON content type, the body length, the
 * millisecond timestamp, the MD5 of the body, and the path plus the *alphabetically
 * sorted* query string. The query has to be part of the signed string: signing
 * `/subject-api/get` while sending `?subjectId=…` is rejected with `407`.
 *
 * The key is double-base64 encoded. This object is pure JVM so the exact bytes the
 * phone signs can be tested on a desktop.
 */
object MobileSigner {

    /** Primary key from the mobile app (double-base64 encoded). */
    const val SECRET_KEY_DEFAULT_B64: String =
        "NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw=="

    /** Alternate key used by some app builds. */
    const val SECRET_KEY_ALT_B64: String =
        "WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ=="

    /** Maximum number of body bytes the signature covers. */
    private const val BODY_HASH_LIMIT = 102_400

    fun signingKey(encoded: String = SECRET_KEY_DEFAULT_B64): ByteArray {
        val once = Base64.getDecoder().decode(encoded)
        return Base64.getDecoder().decode(String(once, Charsets.UTF_8))
    }

    /** `"<unix seconds>,<md5(reversed seconds)>"` — the lightweight client proof. */
    fun clientToken(epochSeconds: Long = System.currentTimeMillis() / 1000): String {
        val seconds = epochSeconds.toString()
        return "$seconds,${md5Hex(seconds.reversed())}"
    }

    /**
     * The canonical string the server hashes.
     *
     * Query parameters are sorted by key, and values are *not* URL-decoded — the
     * exact request URL is what gets signed.
     */
    fun canonicalString(
        method: String,
        url: String,
        body: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
    ): String {
        val uri = URI(url)
        val path = uri.rawPath.ifBlank { "/" }
        val canonicalPath = if (uri.rawQuery.isNullOrBlank()) {
            path
        } else {
            val pairs = uri.rawQuery
                .split("&")
                .filter { it.isNotEmpty() }
                .map { segment ->
                    val pieces = segment.split("=", limit = 2)
                    pieces[0] to (pieces.getOrNull(1) ?: "")
                }
                .sortedBy { it.first }
            path + "?" + pairs.joinToString("&") { (key, value) -> "$key=$value" }
        }

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyLength = bodyBytes?.takeIf { it.isNotEmpty() }?.size?.toString().orEmpty()
        val bodyHash = bodyBytes
            ?.takeIf { it.isNotEmpty() }
            ?.let { md5Hex(it.copyOf(minOf(it.size, BODY_HASH_LIMIT))) }
            .orEmpty()

        return listOf(
            method.uppercase(),
            "application/json",
            "application/json",
            bodyLength,
            timestampMs.toString(),
            bodyHash,
            canonicalPath,
        ).joinToString("\n")
    }

    /** `"<timestamp ms>|2|<base64(hmac-md5(canonical request))>"`. */
    fun signature(
        method: String,
        url: String,
        body: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
        key: ByteArray = signingKey(),
    ): String {
        val canonical = canonicalString(method, url, body, timestampMs)
        return signatureForCanonical(canonical, timestampMs, key)
    }

    /** Same as [signature], but for a canonical string that was built elsewhere. */
    fun signatureForCanonical(
        canonical: String,
        timestampMs: Long,
        key: ByteArray = signingKey(),
    ): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key, "HmacMD5"))
        val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(digest)
        return "$timestampMs|2|$encoded"
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun md5Hex(value: String): String = md5Hex(value.toByteArray(Charsets.UTF_8))
}