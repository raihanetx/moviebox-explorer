package com.example.movieboxexplorer.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mobile BFF signature.
 *
 * The vectors were produced by the reference implementation in the backend guide:
 * the same canonical string, the same double-base64 key, the same HMAC-MD5.
 */
class MobileSignerTest {

    private val getUrl =
        "https://apig.inmoviebox.com/wefeed-mobile-bff/subject-api/get" +
            "?subjectId=2551537100000000000&se=1&ep=2"

    @Test
    fun `client token is epoch seconds and md5 of the reversed seconds`() {
        assertEquals(
            "1794634872,7d35fdfc105e83cf3cf117655efbcecb",
            MobileSigner.clientToken(epochSeconds = 1794634872),
        )
    }

    @Test
    fun `canonical string includes the sorted query`() {
        val canonical = MobileSigner.canonicalString(
            method = "GET",
            url = getUrl,
            body = null,
            timestampMs = 1794634872000,
        )
        assertEquals(
            listOf(
                "GET",
                "application/json",
                "application/json",
                "",
                "1794634872000",
                "",
                "/wefeed-mobile-bff/subject-api/get" +
                    "?ep=2&se=1&subjectId=2551537100000000000",
            ).joinToString("\n"),
            canonical,
        )
    }

    @Test
    fun `get signature matches the documented vector`() {
        val signature = MobileSigner.signature(
            method = "GET",
            url = getUrl,
            body = null,
            timestampMs = 1794634872000,
        )
        assertEquals("1794634872000|2|BzCBHtGHxTEznPaersNg0A==", signature)
    }

    @Test
    fun `post signature hashes the exact body bytes`() {
        val body = """{"keyword":"Black Torch","page":1,"perPage":20}"""
        val signature = MobileSigner.signature(
            method = "POST",
            url = "https://apig.inmoviebox.com/wefeed-mobile-bff/subject-api/search",
            body = body,
            timestampMs = 1794634872000,
        )
        assertEquals("1794634872000|2|BwRU8p1KAWfn/Dy+GOnJsg==", signature)
    }

    @Test
    fun `the signing key is double-base64 decoded`() {
        val key = MobileSigner.signingKey()
        assertEquals(30, key.size)
        assertEquals(
            "efa891974eecd3148df63aa611602defd101259ba521022c57ae0566bd8e",
            key.joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    @Test
    fun `the alternate key also decodes`() {
        val decoded = Base64.getDecoder()
            .decode(MobileSigner.SECRET_KEY_ALT_B64)
            .toString(Charsets.UTF_8)
        assertTrue(decoded.isNotBlank())
        assertTrue(MobileSigner.signingKey(MobileSigner.SECRET_KEY_ALT_B64).isNotEmpty())
    }
}