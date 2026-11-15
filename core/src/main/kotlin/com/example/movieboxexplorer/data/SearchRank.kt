package com.example.movieboxexplorer.data

/**
 * Client-side ranking for search results.
 *
 * The search API returns language variants as separate records and does not always put
 * the exact title first: searching "Black Torch" can return
 *
 *  1. Black Torch [Hindi]
 *  2. Black Torch
 *  3. ADA KAN TORCH KAN …
 *
 * The user should see the exact title (and the un-suffixed original) first, not a dub.
 * This is a display ranking only — every record is still available.
 */
object SearchRank {

    fun rank(items: List<SubjectBrief>, keyword: String): List<SubjectBrief> {
        val query = normalize(keyword)
        if (query.isBlank()) return items

        return items.sortedWith(
            compareByDescending<SubjectBrief> { score(it, query) }
                .thenBy { it.title.length },
        )
    }

    /**
     * Pick the best candidate for [keyword].
     *
     * Prefers an exact title, then a prefix/substring match. Penalises recaps,
     * compilations, specials, re-edits and movie cuts. Prefers entries that have a
     * resource and a rating.
     */
    fun bestMatch(items: List<SubjectBrief>, keyword: String): SubjectBrief? {
        val query = keyword.trim().lowercase()
        if (query.isBlank() || items.isEmpty()) return null
        return items.maxByOrNull { bestMatchScore(it, query) }
    }

    private fun bestMatchScore(item: SubjectBrief, query: String): Int {
        val title = item.title.lowercase()
        var score = when {
            title == query -> 100
            query in title -> 60
            title.startsWith(query) -> 50
            else -> 0
        }

        BAD_WORDS.forEach { word ->
            if (word in title) score -= 40
        }
        if (item.hasResource) score += 20
        score += (item.imdbRatingValue.toFloatOrNull() ?: 0f).toInt() * 2
        if (item.subjectType == 2) score += 10
        return score
    }

    private val BAD_WORDS = listOf(
        "re-edit",
        "re edit",
        "recap",
        "compilation",
        "summary",
        "mini-anime",
        "special",
        "ova",
        "movie",
        "part ",
    )

    private fun score(item: SubjectBrief, query: String): Int {
        val title = normalize(item.title)
        var score = when {
            title == query -> 1_000
            title.startsWith(query) -> 500
            title.contains(query) -> 200
            else -> 0
        }

        // Prefer the plain title over a language-suffixed variant.
        if (!item.title.contains("[")) score += 50

        return score
    }

    /** Lowercase, strip apostrophes/brackets/punctuation, collapse spaces. */
    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("['’]"), "")
            .replace(Regex("[\\[\\](){}]"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}