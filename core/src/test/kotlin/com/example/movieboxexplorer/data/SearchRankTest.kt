package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Search results are language variants, not guesses. The exact / un-suffixed record
 * must come first, while every variant stays available.
 */
class SearchRankTest {

    private fun subject(title: String) = SubjectBrief(subjectId = title.hashCode().toString(), title = title)

    @Test
    fun `exact title beats a language variant`() {
        val ranked = SearchRank.rank(
            listOf(subject("Black Torch [Hindi]"), subject("Black Torch")),
            "Black Torch",
        )

        assertEquals("Black Torch", ranked.first().title)
    }

    @Test
    fun `a language query finds the bracketed variant`() {
        val ranked = SearchRank.rank(
            listOf(subject("Black Torch"), subject("Black Torch [Hindi]")),
            "Black Torch Hindi",
        )

        assertEquals("Black Torch [Hindi]", ranked.first().title)
    }

    @Test
    fun `the un-suffixed title beats a dub with the same prefix`() {
        val ranked = SearchRank.rank(
            listOf(subject("Spy x Family [Hindi]"), subject("Spy x Family")),
            "Spy x Family",
        )

        assertEquals("Spy x Family", ranked.first().title)
    }

    @Test
    fun `prefix matches beat unrelated titles`() {
        val ranked = SearchRank.rank(
            listOf(
                subject("That Time I Got Reincarnated as a Slime"),
                subject("Frieren: Beyond Journey's End"),
                subject("Frieren: Beyond Journey's End [Hindi]"),
            ),
            "Frieren",
        )

        assertEquals("Frieren: Beyond Journey's End", ranked.first().title)
        assertEquals("Frieren: Beyond Journey's End [Hindi]", ranked[1].title)
    }

    @Test
    fun `case and punctuation are normalised`() {
        val ranked = SearchRank.rank(
            listOf(subject("SPY x FAMILY [Hindi]"), subject("Spy x Family")),
            "spy x family",
        )

        assertEquals("Spy x Family", ranked.first().title)
    }

    @Test
    fun `apostrophes are ignored`() {
        val ranked = SearchRank.rank(
            listOf(subject("Azumi"), subject("Hell's Paradise: Jigokuraku S1-S2")),
            "Hells Paradise",
        )

        assertEquals("Hell's Paradise: Jigokuraku S1-S2", ranked.first().title)
    }

    @Test
    fun `a blank query keeps the original order`() {
        val items = listOf(subject("B"), subject("A"))
        assertEquals(items, SearchRank.rank(items, "  "))
    }
}