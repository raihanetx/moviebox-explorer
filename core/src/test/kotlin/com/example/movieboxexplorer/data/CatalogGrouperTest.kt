package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogGrouperTest {

    private fun subject(
        title: String,
        id: String = title.hashCode().toString(),
        detailPath: String = "",
        cover: String = "",
    ) = SubjectBrief(
        subjectId = id,
        title = title,
        detailPath = detailPath,
        cover = if (cover.isBlank()) null else Cover(url = cover),
    )

    @Test
    fun `language variants are grouped`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Black Clover", "1"),
                subject("Black Clover [Hindi]", "2"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().records.size)
        assertEquals("black clover", groups.single().key)
    }

    @Test
    fun `apostrophes are ignored`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Hell's Paradise", "1"),
                subject("Hell's Paradise [English]", "2"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("hells paradise", groups.single().key)
    }

    @Test
    fun `fullmetal alchemist and brotherhood share a franchise`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Fullmetal Alchemist", "1"),
                subject("Fullmetal Alchemist: Brotherhood", "2"),
                subject("Fullmetal Alchemist: Brotherhood [English]", "3"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().records.size)
    }

    @Test
    fun `baki records share a franchise`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Baki", "1"),
                subject("Baki Hanma", "2"),
                subject("Baki the Grappler [English]", "3"),
                subject("Baki-Dou", "4"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(4, groups.single().records.size)
    }

    @Test
    fun `unrelated titles stay separate`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Black Clover", "1"),
                subject("Black Torch", "2"),
                subject("Frieren", "3"),
            ),
        )

        assertEquals(3, groups.size)
    }

    private fun fullSubject(
        title: String,
        id: String,
        detailPath: String = "",
        subjectType: Int = 2,
        country: String = "",
        genre: String = "",
        hasResource: Boolean = true,
        webHighRisk: Boolean = false,
        imdb: String = "",
    ) = SubjectBrief(
        subjectId = id,
        title = title,
        detailPath = detailPath,
        subjectType = subjectType,
        countryName = country,
        genre = genre,
        hasResource = hasResource,
        webHighRisk = webHighRisk,
        imdbRatingValue = imdb,
    )

    @Test
    fun `language variants collapse into one card`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Black Clover S1-S2", "master", detailPath = "black-clover-x"),
                fullSubject("Black Clover [English]", "english"),
                fullSubject("Black Clover [Hindi]", "hindi"),
            ),
        )

        assertEquals(1, result.size)
        assertEquals("master", result.single().subjectId)
    }

    @Test
    fun `the record without a language tag wins over a tagged dub`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Attack on Titan [English]", "english",
                    detailPath = "aot-english-x", country = "Japan", genre = "Anime, Action"),
                fullSubject("Attack on Titan", "japanese",
                    detailPath = "aot-x", country = "Japan", genre = "Anime, Action"),
            ),
        )

        assertEquals(1, result.size)
        assertEquals("japanese", result.single().subjectId)
    }

    @Test
    fun `same title but different country stays separate`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Attack on Titan", "us", subjectType = 1,
                    country = "United States", genre = "Action, Adventure"),
                fullSubject("Attack on Titan", "jp", subjectType = 2,
                    country = "Japan", genre = "Anime, Action"),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `same title but different genre stays separate`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Crimson", "drama", genre = "Drama, Romance", country = "Japan"),
                fullSubject("Crimson", "action", genre = "Action, Thriller", country = "Japan"),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `duplicate uploads of the same dub collapse`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Attack on Titan: Chronicle [English]", "en",
                    detailPath = "chronicle-en-x", country = "Japan", genre = "Anime, Action",
                    webHighRisk = true),
                fullSubject("Attack on Titan: Chronicle", "a",
                    detailPath = "chronicle-a-x", country = "Japan", genre = "Anime, Action"),
                fullSubject("Attack on Titan: Chronicle", "b",
                    detailPath = "chronicle-b-x", country = "Japan", genre = "Anime, Action"),
            ),
        )

        assertEquals(1, result.size)
        assertTrue(result.single().detailPath.isNotBlank())
    }

    @Test
    fun `unrelated titles are left alone`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Black Clover", "bc", genre = "Anime, Action", country = "Japan"),
                fullSubject("Black Torch", "bt", genre = "Anime, Action", country = "Japan"),
                fullSubject("Frieren", "fr", genre = "Anime, Adventure", country = "Japan"),
            ),
        )

        assertEquals(3, result.size)
    }

    @Test
    fun `the order of first appearance is preserved`() {
        val result = CatalogGrouper.dedupeForDisplay(
            listOf(
                fullSubject("Zebra [English]", "z2"),
                fullSubject("Mango [English]", "m2"),
                fullSubject("Zebra", "z1", detailPath = "zebra-x"),
                fullSubject("Mango", "m1", detailPath = "mango-x"),
            ),
        )

        assertEquals(listOf("z1", "m1"), result.map { it.subjectId })
    }

    @Test
    fun `the canonical record is the one with a detail path`() {
        val groups = CatalogGrouper.group(
            listOf(
                subject("Black Clover", "1", detailPath = ""),
                subject("Black Clover", "2", detailPath = "black-clover-cE1aW4X3H14", cover = "https://img"),
            ),
        )

        assertEquals("2", groups.single().canonical.subjectId)
        assertTrue(groups.single().canonical.detailPath.isNotBlank())
    }
}