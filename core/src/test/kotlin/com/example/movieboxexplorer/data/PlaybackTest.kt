package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rendition the app picks, and the headers the CDN needs.
 *
 * The shapes below are taken from a real `GET /subject/play` response for
 * Black Torch (subjectId 6983322689440465144, season 1, episode 1).
 */
class PlaybackTest {

    private val headers = mapOf(
        "Referer" to "https://mzfi.me/",
        "User-Agent" to "Mozilla/5.0",
    )

    private fun mp4(resolution: String, vipLocked: Boolean = false) = PlayStream(
        id = "id-$resolution",
        url = "https://cdn.example/bt/$resolution.mp4?sign=abc",
        format = "MP4",
        resolutions = resolution,
        size = "393817831",
        duration = 1425,
        codecName = "h264",
        vipLocked = vipLocked,
    )

    private val dash = PlayStream(
        id = "dash-1",
        url = "https://cdn.example/dash/…/index_web.mpd",
        format = "DASH",
        resolutions = "1080,720,480",
        size = "562259577",
        duration = 1425,
        codecName = "hevc",
        signCookie = "Edge-Cache-Cookie=urlprefix=aHR0cHM6…:sign=2d4b5070…:t=1794569595",
        signHeaderKey = "X-MB-Token",
    )

    @Test
    fun `progressive mp4 is preferred over the adaptive manifest`() {
        val data = PlayData(streams = listOf(mp4("1080")), dash = listOf(dash), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals("MP4", source.format)
        assertEquals("https://cdn.example/bt/1080.mp4?sign=abc", source.url)
    }

    @Test
    fun `a preferred resolution selects the closest available file`() {
        val data = PlayData(streams = listOf(mp4("360"), mp4("1080"), mp4("480")), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers, preferredResolution = 720))
        assertEquals(480, source.resolution)
    }

    @Test
    fun `an exact preferred resolution wins`() {
        val data = PlayData(streams = listOf(mp4("360"), mp4("1080"), mp4("480")), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers, preferredResolution = 480))
        assertEquals(480, source.resolution)
        assertEquals("https://cdn.example/bt/480.mp4?sign=abc", source.url)
    }

    @Test
    fun `the highest available mp4 wins`() {
        val data = PlayData(
            streams = listOf(mp4("360"), mp4("1080"), mp4("480")),
            hasResource = true,
        )
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals(1080, source.resolution)
        assertEquals("https://cdn.example/bt/1080.mp4?sign=abc", source.url)
    }

    @Test
    fun `mp4 needs no credential header`() {
        val data = PlayData(streams = listOf(mp4("480")), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals(headers, source.headers)
    }

    @Test
    fun `the adaptive manifest is used when there is no mp4`() {
        val data = PlayData(dash = listOf(dash), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals("DASH", source.format)
        assertEquals(1080, source.resolution)
    }

    @Test
    fun `the adaptive manifest carries the edge cache credential`() {
        val data = PlayData(dash = listOf(dash), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers))
        // The credential goes in verbatim, under the header the source names.
        assertEquals(dash.signCookie, source.headers["X-MB-Token"])
        assertEquals("https://mzfi.me/", source.headers["Referer"])
    }

    @Test
    fun `an adaptive manifest without a credential gets no extra header`() {
        val bare = dash.copy(signCookie = "", signHeaderKey = "")
        val data = PlayData(dash = listOf(bare), hasResource = true)
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals(headers, source.headers)
    }

    @Test
    fun `locked renditions are skipped`() {
        val data = PlayData(
            streams = listOf(mp4("1080", vipLocked = true), mp4("480")),
            hasResource = true,
        )
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals(480, source.resolution)
    }

    @Test
    fun `an episode that was never uploaded has no source`() {
        val data = PlayData(hasResource = false)
        assertNull(selectPlaybackSource(data, headers))
    }

    @Test
    fun `a resolution ladder is read from the highest quality`() {
        assertEquals(1080, dash.resolution)
        assertEquals(1080, mp4("1080").resolution)
        assertEquals(0, mp4("").resolution)
    }

    @Test
    fun `quality and codec labels are used when the H5 fields are absent`() {
        val stream = PlayStream(
            url = "https://bcdnxw.hakunaymatata.com/bt/asset.mp4?sign=abc&t=1794634375",
            format = "MP4",
            quality = "1080",
            height = 1080,
            codec = "h264",
        )
        assertEquals(1080, stream.resolution)
        assertEquals("h264", stream.effectiveCodec)
        assertEquals(false, stream.isPromo)
    }

    @Test
    fun `promo renditions are skipped`() {
        val promo = PlayStream(
            url = "https://macdn.aoneroom.com/other/2026/10/10/80da52722ee7de7a4303290af6783424.mp4",
            format = "MP4",
            resolutions = "1080",
        )
        val data = PlayData(
            streams = listOf(promo, mp4("720")),
            hasResource = true,
        )
        val source = assertNotNull(selectPlaybackSource(data, headers))
        assertEquals(720, source.resolution)
        assertEquals(false, source.url.contains("macdn.aoneroom.com"))
    }

    @Test
    fun `an all-promo response has no playable source`() {
        val promo = PlayStream(
            url = "https://macdn.aoneroom.com/other/2026/10/10/80da52722ee7de7a4303290af6783424.mp4",
            format = "MP4",
            resolutions = "1080",
        )
        val data = PlayData(streams = listOf(promo), hasResource = true)
        assertNull(selectPlaybackSource(data, headers))
    }

    @Test
    fun `a vip locked response is reported as locked`() {
        assertEquals(
            "This episode needs MovieBox Premium",
            PlayData(vipLocked = true).lockReason(),
        )
        assertNull(PlayData().lockReason())
    }

    @Test
    fun `an access strategy that demands a vip level is reported as locked`() {
        val locked = PlayData(
            accessStrategy = AccessStrategy(ruleType = 1, requiredVipLevel = 1, freeEpisodeCount = 0),
        )
        assertNotNull(locked.lockReason())

        // A track that grants some free episodes is not locked.
        val free = PlayData(
            accessStrategy = AccessStrategy(ruleType = 1, requiredVipLevel = 1, freeEpisodeCount = 2),
        )
        assertNull(free.lockReason())
    }
}
