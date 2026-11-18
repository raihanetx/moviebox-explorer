package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nigeria is excluded everywhere, India only from animation listings. */
class ContentPolicyTest {

    private fun brief(title: String, country: String) = SubjectBrief(
        subjectId = title,
        title = title,
        countryName = country,
        detailPath = title,
    )

    @Test
    fun `normalization strips punctuation and case`() {
        assertEquals("nigeria", ContentPolicy.normalize("Nigeria."))
        assertEquals("nigeria", ContentPolicy.normalize(" NIGERIA "))
        assertEquals("hongkong china", ContentPolicy.normalize("Hongkong, China"))
        assertEquals("", ContentPolicy.normalize(""))
    }

    @Test
    fun `nigeria matches punctuation variants but not lookalikes`() {
        assertTrue(ContentPolicy.isNigeria("Nigeria"))
        assertTrue(ContentPolicy.isNigeria("nigeria (NG)"))
        assertTrue(ContentPolicy.isNigeria("Nigeria, Ghana"))
        assertFalse(ContentPolicy.isNigeria("Nigerialand"))
        assertFalse(ContentPolicy.isNigeria("United States"))
    }

    @Test
    fun `blank country is never hidden`() {
        val blank = brief("Unknown", "")
        assertFalse(ContentPolicy.hiddenEverywhere(blank))
        assertFalse(ContentPolicy.hiddenInAnimation(blank))
        assertFalse(ContentPolicy.isNigeria(""))
        assertFalse(ContentPolicy.isIndia(""))
    }

    @Test
    fun `movies keep India drop Nigeria`() {
        val items = listOf(
            brief("us", "United States"),
            brief("ng", "Nigeria"),
            brief("in", "India"),
            brief("blank", ""),
        )
        val kept = ContentPolicy.keep(items, animation = false).map { it.title }
        assertEquals(listOf("us", "in", "blank"), kept)
    }

    @Test
    fun `animation drops both India and Nigeria`() {
        val items = listOf(
            brief("us", "United States"),
            brief("ng", "Nigeria"),
            brief("in", "India"),
            brief("blank", ""),
        )
        val kept = ContentPolicy.keep(items, animation = true).map { it.title }
        assertEquals(listOf("us", "blank"), kept)
    }

    @Test
    fun `an all-Nigeria section collapses to null`() {
        val section = OperatingSection(
            type = "SUBJECTS_MOVIE",
            title = "Nollywood Movie",
            subjects = listOf(brief("n1", "Nigeria"), brief("n2", "Nigeria")),
        )
        assertNull(ContentPolicy.filterSection(section))
    }

    @Test
    fun `a mixed section keeps only the allowed items`() {
        val section = OperatingSection(
            type = "SUBJECTS_MOVIE",
            title = "Popular Series",
            subjects = listOf(brief("us", "United States"), brief("ng", "Nigeria")),
            rankingSubjectList = listOf(brief("rank-in", "India"), brief("rank-ng", "Nigeria")),
            banner = Banner(
                items = listOf(
                    BannerItem(title = "bn-ng", subject = brief("bn-ng", "Nigeria")),
                    BannerItem(title = "bn-us", subject = brief("bn-us", "United States")),
                    BannerItem(title = "bn-unknown", subject = null),
                ),
            ),
        )
        val kept = ContentPolicy.filterSection(section, animation = false)!!
        assertEquals(listOf("us"), kept.subjects.map { it.title })
        assertEquals(listOf("rank-in"), kept.rankingSubjectList.map { it.title })
        assertEquals(listOf("bn-us", "bn-unknown"), kept.banner!!.items.map { it.title })
    }

    @Test
    fun `a section left with only a banner survives`() {
        val section = OperatingSection(
            type = "BANNER",
            title = "Banner",
            subjects = listOf(brief("ng", "Nigeria")),
            banner = Banner(items = listOf(BannerItem(title = "bn-us", subject = brief("bn-us", "United States")))),
        )
        val kept = ContentPolicy.filterSection(section)!!
        assertTrue(kept.subjects.isEmpty())
        assertEquals(1, kept.banner!!.items.size)
    }

    @Test
    fun `an entirely empty banner section is dropped`() {
        val section = OperatingSection(
            type = "BANNER",
            title = "Banner",
            subjects = listOf(brief("ng", "Nigeria")),
            banner = Banner(items = listOf(BannerItem(title = "bn-ng", subject = brief("bn-ng", "Nigeria")))),
        )
        assertNull(ContentPolicy.filterSection(section))
    }

    @Test
    fun `filter values hide Nigeria everywhere and India only in animation`() {
        val configs = listOf(
            FilterConfig(
                channelId = 1006,
                channelName = "Anime",
                items = listOf(
                    FilterGroup(
                        filterType = "country",
                        title = "Country",
                        filterVals = listOf(
                            FilterValue("Nigeria", "Nigeria"),
                            FilterValue("India", "India"),
                            FilterValue("Japan", "Japan"),
                        ),
                    ),
                    FilterGroup(
                        filterType = "genre",
                        title = "Genre",
                        filterVals = listOf(FilterValue("Drama", "Drama")),
                    ),
                ),
            ),
        )
        val tv = ContentPolicy.hideFilterValues(configs, animation = false)
        assertEquals(listOf("India", "Japan"), tv.first().items[0].filterVals.map { it.id })
        assertEquals(listOf("Drama"), tv.first().items[1].filterVals.map { it.id })
        val anime = ContentPolicy.hideFilterValues(configs, animation = true)
        assertEquals(listOf("Japan"), anime.first().items[0].filterVals.map { it.id })
    }

    @Test
    fun `hiddenFilterValue never touches non-country values`() {
        assertFalse(ContentPolicy.hiddenFilterValue("Drama", animation = true))
        assertFalse(ContentPolicy.hiddenFilterValue("Hongkong, China", animation = true))
        assertTrue(ContentPolicy.hiddenFilterValue("Nigeria", animation = false))
        assertFalse(ContentPolicy.hiddenFilterValue("India", animation = false))
        assertTrue(ContentPolicy.hiddenFilterValue("India", animation = true))
    }
}
