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

    // ---------------------------------------------------------- list dedup

    /**
     * Collapse language variants of the same upload into one card.
     *
     * The catalogue lists each language track separately ("Title", "Title
     * [English]", …). For the browse surfaces we show one card per upload and keep
     * the variants one tap away inside the detail page's language picker.
     *
     * The rule is deliberately narrow:
     *
     *  1. the key strips only language brackets and season-pack suffixes;
     *  2. two records merge only when the key matches **and** they are compatible —
     *     same `subjectType` (when both report it), same `countryName` (when both
     *     report it), and at least one shared genre token (when both report it);
     *  3. within a cluster the card goes to the record with a usable `detailPath`,
     *     `hasResource`, no language tag in its title, an IMDb value, and the lower
     *     risk flag.
     *
     * An incompatible split stays as two cards rather than merging the wrong
     * uploads.
     */
    fun dedupeForDisplay(items: List<SubjectBrief>): List<SubjectBrief> {
        val byKey = LinkedHashMap<String, MutableList<SubjectBrief>>()
        for (item in items) {
            val key = displayKey(item.title)
                .ifBlank { "id:${item.subjectId.ifBlank { item.detailPath }}" }
            byKey.getOrPut(key) { mutableListOf() }.add(item)
        }

        val result = mutableListOf<SubjectBrief>()
        for ((_, group) in byKey) {
            val clusters = mutableListOf<MutableList<SubjectBrief>>()
            for (item in group) {
                val target = clusters.firstOrNull { cluster ->
                    cluster.all { member -> compatible(member, item) }
                }
                if (target != null) target.add(item) else clusters.add(mutableListOf(item))
            }
            clusters.forEach { cluster -> result += canonicalOf(cluster) }
        }
        return result
    }

    private fun displayKey(title: String): String =
        title.lowercase()
            .replace(Regex("['’]"), "")
            .replace(Regex("\\[[^]]*\\]"), " ")
            .replace(Regex("\\bs\\d+(\\s*-\\s*s?\\d+)*\\b"), " ")
            .replace(Regex("\\bseason\\s*\\d+\\b"), " ")
            .replace(Regex("\\bpart\\s*\\d+\\b"), " ")
            .replace(Regex("\\bvol\\.?\\s*\\d+\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun genreTokens(genre: String): Set<String> =
        genre.lowercase().split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun compatible(a: SubjectBrief, b: SubjectBrief): Boolean {
        // Same upload type (both reported): a movie never merges with a series.
        if (a.subjectType != 0 && b.subjectType != 0 && a.subjectType != b.subjectType) {
            return false
        }
        // Same production country (both reported): the US movie and the Japan series
        // of the same name stay separate.
        if (a.countryName.isNotBlank() && b.countryName.isNotBlank() &&
            !a.countryName.equals(b.countryName, ignoreCase = true)
        ) {
            return false
        }
        // At least one shared genre token (both reported): same-name unrelated
        // productions stay separate.
        val genresA = genreTokens(a.genre)
        val genresB = genreTokens(b.genre)
        if (genresA.isNotEmpty() && genresB.isNotEmpty() &&
            genresA.intersect(genresB).isEmpty()
        ) {
            return false
        }
        return true
    }

    /** Lower rank wins: usable path → resource → no language tag → IMDb → lower risk. */
    private fun canonicalRank(subject: SubjectBrief): Int {
        var rank = 0
        if (subject.detailPath.isBlank()) rank += 8
        if (!subject.hasResource) rank += 4
        if (hasLanguageTag(subject.title)) rank += 2
        if (subject.imdbRatingValue.toFloatOrNull() == null) rank += 1
        if (subject.webHighRisk) rank += 1
        return rank
    }

    private fun canonicalOf(group: List<SubjectBrief>): SubjectBrief =
        group.minByOrNull { canonicalRank(it) } ?: group.first()

    private fun hasLanguageTag(title: String): Boolean =
        Regex("\\[[^]]+\\]").containsMatchIn(title)
}