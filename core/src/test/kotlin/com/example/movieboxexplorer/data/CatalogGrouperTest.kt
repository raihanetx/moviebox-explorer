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