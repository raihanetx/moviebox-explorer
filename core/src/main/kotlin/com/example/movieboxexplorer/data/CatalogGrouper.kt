package com.example.movieboxexplorer.data

/**
 * Groups catalogue records that belong to the same title or franchise.
 *
 * The catalogue returns language variants and season splits as separate records.
 * This groups them by a normalized title, with a small franchise prefix map for
* franchises whose records do not share the exact same base name (Fullmetal
 * Alchemist / Brotherhood, Baki / Baki Hanma, Baahubali seasons).
 */
object CatalogGrouper {

    fun group(records: List<SubjectBrief>): List<CatalogGroup> {
        val usable = records.filter {
            it.subjectId.isNotBlank() || it.detailPath.isNotBlank()
        }

        return usable
            .groupBy { franchiseKey(normalize(it.title)) }
            .filter { it.key.isNotBlank() }
            .map { (key, group) ->
                val canonical = group.maxByOrNull { score(it) } ?: group.first()
                CatalogGroup(
                    key = key,
                    title = canonical.title,
                    records = group,
                    canonical = canonical,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /** Lowercase, strip apostrophes/brackets/punctuation and season tags. */
    fun normalize(title: String): String =
        title.lowercase()
            .replace(Regex("['’]"), "")
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\bs\\d+\\b"), " ")
            .replace(Regex("\\bseason\\s*\\d+\\b"), " ")
            .replace(Regex("\\bvol\\.?\\s*\\d+\\b"), " ")
            .replace(Regex("\\bpart\\s*\\d+\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun franchiseKey(normalized: String): String = when {
        normalized.startsWith("fullmetal alchemist") -> "fullmetal alchemist"
        normalized.startsWith("baki") -> "baki"
        normalized.startsWith("baahubali") -> "baahubali"
        normalized.startsWith("black clover") -> "black clover"
        normalized.startsWith("hells paradise") -> "hells paradise"
        else -> normalized
    }

    private fun score(subject: SubjectBrief): Int {
        var score = 0
        if (subject.detailPath.isNotBlank()) score += 20
        if (!subject.cover?.url.isNullOrBlank()) score += 10
        if (subject.releaseDate.isNotBlank()) score += 5
        if (subject.genre.isNotBlank()) score += 5
        if (subject.imdbRatingValue.isNotBlank()) score += 5
        if (subject.hasResource) score += 10
        if (subject.dubs.isNotEmpty()) score += 5
        return score
    }
}