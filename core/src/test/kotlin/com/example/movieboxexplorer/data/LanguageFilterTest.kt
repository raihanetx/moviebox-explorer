package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageFilterTest {

    private fun language(
        label: String,
        code: String,
        kind: String = "Dub",
        original: Boolean = false,
    ) = LanguageInfo(
        label = label,
        code = code,
        kind = kind,
        isOriginal = original,
        episodes = 12,
        seasons = emptyList(),
        resolutions = emptyList(),
        uploader = "",
        detailPath = "",
        subjectId = label.hashCode().toString(),
    )

    @Test
    fun `only original english and hindi dubs are shown`() {
        val visible = LanguageFilter.visible(
            listOf(
                language("Original Audio", "ja", original = true),
                language("English dub", "en"),
                language("Hindi dub", "hi"),
                language("Spanish dub", "es"),
                language("French dub", "fr"),
                language("Arabic sub", "ar", kind = "Subtitle"),
            ),
        )

        assertEquals(
            listOf("Original Audio", "English dub", "Hindi dub"),
            visible.map { it.label },
        )
    }

    @Test
    fun `without english or hindi only the original remains`() {
        val visible = LanguageFilter.visible(
            listOf(
                language("Original Audio", "ja", original = true),
                language("Spanish dub", "es"),
                language("Tamil dub", "ta"),
            ),
        )

        assertEquals(listOf("Original Audio"), visible.map { it.label })
    }

    @Test
    fun `subtitles are never shown`() {
        val visible = LanguageFilter.visible(
            listOf(
                language("Original Audio", "ja", original = true),
                language("Hindi sub", "hi", kind = "Subtitle"),
                language("English sub", "en", kind = "Subtitle"),
            ),
        )

        assertEquals(listOf("Original Audio"), visible.map { it.label })
    }

    @Test
    fun `other dubs are never shown`() {
        val visible = LanguageFilter.visible(
            listOf(
                language("Original Audio", "ja", original = true),
                language("Tamil dub", "ta"),
                language("Telugu dub", "te"),
                language("ptbr dub", "ptbr"),
            ),
        )

        assertEquals(listOf("Original Audio"), visible.map { it.label })
    }

    @Test
    fun `case is ignored`() {
        val visible = LanguageFilter.visible(
            listOf(
                language("Original Audio", "ja", original = true),
                language("English dub", "EN"),
                language("Hindi dub", "HI"),
            ),
        )

        assertEquals(3, visible.size)
    }
}