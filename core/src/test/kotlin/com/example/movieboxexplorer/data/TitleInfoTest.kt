package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The completeness measurement the detail screen shows, and how the risk flags of a
 * title and of its language tracks combine.
 */
class TitleInfoTest {

    private fun track(
        label: String,
        episodes: Int,
        seasons: List<SeasonInfo> = emptyList(),
        adult: Boolean = false,
    ) = LanguageInfo(
        label = label,
        code = label.take(2).lowercase(),
        kind = "Dub",
        isOriginal = false,
        episodes = episodes,
        seasons = seasons,
        resolutions = emptyList(),
        uploader = "",
        detailPath = "slug-$label",
        subjectId = "id-$label",
        isPornographic = adult,
    )

    private fun subject(
        webHighRisk: Boolean = false,
        isPornographic: Boolean = false,
    ) = Subject(
        subjectId = "1",
        subjectType = 2,
        title = "A Title",
        webHighRisk = webHighRisk,
        isPornographic = isPornographic,
    )

    private fun info(
        subject: Subject = subject(),
        languages: List<LanguageInfo> = emptyList(),
        referenceEpisodes: Int = 0,
        referenceSeasons: Int = 0,
        isForbidden: Boolean = false,
    ) = TitleInfo(
        subject = subject,
        stars = emptyList(),
        languages = languages,
        referenceEpisodes = referenceEpisodes,
        referenceSeasons = referenceSeasons,
        isForbidden = isForbidden,
    )

    @Test
    fun `a complete track is missing nothing`() {
        val original = track("Original", 12, listOf(SeasonInfo(1, 12, emptyList())))
        val hindi = track("Hindi", 11, listOf(SeasonInfo(1, 11, emptyList())))
        val info = info(languages = listOf(original, hindi), referenceEpisodes = 12, referenceSeasons = 1)

        assertEquals(0, info.missingEpisodes(original))
        assertEquals(1, info.missingEpisodes(hindi))
        assertEquals(0, info.missingSeasons(original))
        assertEquals(0, info.missingSeasons(hindi))
    }

    @Test
    fun `a track that covers fewer seasons is reported as missing one`() {
        val original = track("Original", 24, listOf(SeasonInfo(1, 12, emptyList()), SeasonInfo(2, 12, emptyList())))
        val russian = track("Russian", 24, listOf(SeasonInfo(1, 24, emptyList())))
        val info = info(languages = listOf(original, russian), referenceEpisodes = 24, referenceSeasons = 2)

        assertEquals(2, original.seasonCount)
        assertEquals(1, russian.seasonCount)
        assertEquals(1, info.missingSeasons(russian))
    }

    @Test
    fun `missing counts never go negative`() {
        val longer = track("Longer", 12, listOf(SeasonInfo(1, 12, emptyList())))
        val shorter = track("Shorter", 7, listOf(SeasonInfo(1, 7, emptyList())))
        val info = info(languages = listOf(longer, shorter), referenceEpisodes = 7, referenceSeasons = 1)

        assertEquals(0, info.missingEpisodes(longer))
        assertEquals(0, info.missingSeasons(longer))
    }

    @Test
    fun `the risk flags of a title and its tracks combine`() {
        val info = info(
            subject = subject(webHighRisk = true),
            languages = listOf(track("Original", 12), track("Hindi", 11, adult = true)),
        )

        assertTrue(info.risk.unlisted)
        assertTrue(info.risk.adult)
        assertEquals(RiskLevel.Adult, info.risk.level)
    }

    @Test
    fun `an adult track under a clean parent still makes the title adult`() {
        val info = info(
            subject = subject(),
            languages = listOf(track("Original", 12), track("Special", 1, adult = true)),
        )
        assertTrue(info.risk.adult)
    }

    @Test
    fun `a clean title stays clean`() {
        val info = info(subject = subject(), languages = listOf(track("Original", 12)))
        assertEquals(RiskLevel.Normal, info.risk.level)
        assertFalse(info.risk.blocked)
    }

    @Test
    fun `a forbidden title is blocked`() {
        val info = info(isForbidden = true)
        assertTrue(info.risk.blocked)
        assertEquals(RiskLevel.Forbidden, info.risk.level)
    }
}
