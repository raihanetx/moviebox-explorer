package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The season/episode maths that fixes:
 *
 *  - one season delivered as several entries (4 + 20 → 24);
 *  - duplicate records for the same season (24 + 24 → 24, not 48);
 *  - a combined record (24 + 48 → 48, not 72);
 *  - H5 partial season merged with the mobile full season (4 + 24 → 24).
 */
class SeasonMathTest {

    private fun season(
        se: Int,
        maxEp: Int = 0,
        allEp: String = "",
        resolutions: List<Resolution> = emptyList(),
    ) = Season(se = se, maxEp = maxEp, allEp = allEp, resolutions = resolutions)

    private fun resource(vararg seasons: Season) = Resource(seasons = seasons.toList())

    @Test
    fun `split season entries are merged into one season`() {
        val result = SeasonMath.seasonsOf(
            resource(
                season(1, maxEp = 24),
                season(2, maxEp = 4),
                season(2, maxEp = 20),
            ),
        )

        assertEquals(2, result.size)
        val s2 = result.single { it.season == 2 }
        assertEquals(24, s2.episodes)
    }

    @Test
    fun `allEp episode numbers are unioned`() {
        val result = SeasonMath.seasonsOf(
            resource(
                season(2, allEp = "1,2,3,4"),
                season(2, allEp = "5,6,7,8"),
            ),
        )

        assertEquals(8, result.single().episodes)
    }

    @Test
    fun `duplicate season entries are not double counted`() {
        val result = SeasonMath.seasonsOf(
            resource(
                season(2, maxEp = 24),
                season(2, maxEp = 24),
            ),
        )

        assertEquals(24, result.single().episodes)
    }

    @Test
    fun `a combined record keeps the larger value`() {
        val result = SeasonMath.seasonsOf(
            resource(
                season(2, maxEp = 24),
                season(2, maxEp = 48),
            ),
        )

        assertEquals(48, result.single().episodes)
    }

    @Test
    fun `resolution count is used when maxEp is missing`() {
        val result = SeasonMath.seasonsOf(
            resource(
                season(2, resolutions = listOf(Resolution(1080, 24))),
            ),
        )

        assertEquals(24, result.single().episodes)
    }

    @Test
    fun `H5 partial season is replaced by the mobile full season`() {
        val h5 = resource(season(2, maxEp = 4))
        val mobile = resource(season(2, maxEp = 24))

        val merged = SeasonMath.mergeResources(h5, mobile)
        val result = SeasonMath.seasonsOf(merged)

        assertEquals(24, result.single().episodes)
    }

    @Test
    fun `merge keeps the larger season when both have data`() {
        val h5 = resource(
            season(1, maxEp = 24),
            season(2, maxEp = 24),
        )
        val mobile = resource(
            season(1, maxEp = 24),
            season(2, maxEp = 48),
        )

        val result = SeasonMath.seasonsOf(SeasonMath.mergeResources(h5, mobile))

        assertEquals(24, result.single { it.season == 1 }.episodes)
        assertEquals(48, result.single { it.season == 2 }.episodes)
    }

    @Test
    fun `merge unions allEp lists`() {
        val h5 = resource(season(2, allEp = "1,2,3,4"))
        val mobile = resource(season(2, allEp = "5,6,7,8"))

        val result = SeasonMath.seasonsOf(SeasonMath.mergeResources(h5, mobile))

        assertEquals(8, result.single().episodes)
    }

    @Test
    fun `null resources are handled`() {
        assertNull(SeasonMath.mergeResources(null, null))
        assertEquals(1, SeasonMath.mergeResources(null, resource(season(1, maxEp = 1)))?.seasons?.size)
        assertEquals(1, SeasonMath.mergeResources(resource(season(1, maxEp = 1)), null)?.seasons?.size)
    }

    @Test
    fun `resolutions are summed per quality`() {
        val result = SeasonMath.mergeResolutions(
            listOf(
                SeasonInfo(1, 24, listOf(Resolution(1080, 24))),
                SeasonInfo(2, 24, listOf(Resolution(1080, 24), Resolution(720, 10))),
            ),
        )

        assertEquals(2, result.size)
        assertEquals(48, result.single { it.resolution == 1080 }.epNum)
        assertEquals(10, result.single { it.resolution == 720 }.epNum)
        assertTrue(result.first().resolution >= result.last().resolution)
    }

    private fun language(
        label: String,
        code: String,
        seasons: List<SeasonInfo>,
        original: Boolean = false,
    ) = LanguageInfo(
        label = label,
        code = code,
        kind = "Dub",
        isOriginal = original,
        episodes = seasons.sumOf { it.episodes },
        seasons = seasons,
        resolutions = emptyList(),
        uploader = "",
        detailPath = "",
        subjectId = label.hashCode().toString(),
    )

    @Test
    fun `a dub season is mapped to the reference season with the same episode count`() {
        val reference = listOf(
            SeasonInfo(1, 12, emptyList()),
            SeasonInfo(2, 27, emptyList()),
        )
        val english = language("English dub", "en", listOf(SeasonInfo(5, 12, emptyList())))

        val normalized = SeasonMath.normalizeLanguages(reference, listOf(english)).single()

        assertEquals(1, normalized.seasons.single().season)
        assertEquals(12, normalized.episodes)
    }

    @Test
    fun `an exact season number wins over episode-count matching`() {
        val reference = listOf(
            SeasonInfo(1, 12, emptyList()),
            SeasonInfo(2, 7, emptyList()),
        )
        val french = language("French dub", "fr", listOf(SeasonInfo(2, 4, emptyList())))

        val normalized = SeasonMath.normalizeLanguages(reference, listOf(french)).single()

        assertEquals(2, normalized.seasons.single().season)
        assertEquals(4, normalized.episodes)
    }

    @Test
    fun `an unknown season is kept as-is`() {
        val reference = listOf(
            SeasonInfo(1, 12, emptyList()),
            SeasonInfo(2, 27, emptyList()),
        )
        val dub = language("Arabic dub", "ar", listOf(SeasonInfo(3, 13, emptyList())))

        val normalized = SeasonMath.normalizeLanguages(reference, listOf(dub)).single()

        assertEquals(3, normalized.seasons.single().season)
        assertEquals(13, normalized.episodes)
    }

    @Test
    fun `the original track is never remapped`() {
        val reference = listOf(SeasonInfo(1, 12, emptyList()))
        val original = language("Original Audio", "ja", listOf(SeasonInfo(5, 12, emptyList())), original = true)

        val normalized = SeasonMath.normalizeLanguages(reference, listOf(original)).single()

        assertEquals(5, normalized.seasons.single().season)
    }
}