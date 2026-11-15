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