package com.example.movieboxexplorer.data

/** Result of validating one title group. */
data class GroupValidation(
    val key: String,
    val title: String,
    val records: Int,
    val languages: Int,
    val seasons: Int,
    val episodes: Int,
    val issues: List<String>,
)

/** Summary of a catalog validation run. */
data class ValidationSummary(
    val groups: Int,
    val records: Int,
    val clean: Int,
    val warnings: Int,
    val results: List<GroupValidation>,
)

/**
 * Cross-validates a [CatalogGroup] against the live catalogue.
 *
 * For every record in the group it loads the full title, then checks:
 *
 *  - whether the group has any detail record;
 *  - whether it has any language track;
 *  - whether it has any episodes;
 *  - whether different languages disagree about a season’s episode count.
 *
* The validator does not invent data. It reports the conflicts so the UI can show
 * them instead of silently displaying a wrong count.
 */
object CatalogValidator {

    suspend fun validateGroup(
        group: CatalogGroup,
        repository: MovieBoxRepository,
    ): GroupValidation {
        val infos = group.records.mapNotNull { record ->
            if (record.detailPath.isBlank()) return@mapNotNull null
            runCatching {
                repository.loadTitle(record.detailPath, record.subjectId)
            }.getOrNull()
        }

        val languages = infos
            .flatMap { it.languages }
            .distinctBy { it.subjectId.ifBlank { it.detailPath } }

        val seasons = languages
            .flatMap { it.seasons }
            .groupBy { it.season }
            .map { (_, entries) -> entries.maxOf { it.episodes } }

        val issues = mutableListOf<String>()
        if (infos.isEmpty()) issues += "no detail record"
        if (languages.isEmpty()) issues += "no language tracks"
        if (seasons.isNotEmpty() && seasons.all { it == 0 }) issues += "no episodes"

        val conflicts = languages
            .flatMap { language -> language.seasons.map { it.season to it.episodes } }
            .groupBy { it.first }
            .filter { (_, entries) -> entries.map { it.second }.toSet().size > 1 }

        conflicts.forEach { (season, entries) ->
            issues += "S$season count conflict: " +
                entries.joinToString("/") { it.second.toString() }
        }

        return GroupValidation(
            key = group.key,
            title = group.title,
            records = group.records.size,
            languages = languages.size,
            seasons = seasons.size,
            episodes = seasons.sum(),
            issues = issues,
        )
    }

    /**
     * Validate the first [limit] groups. The summary reports the full catalog size
     * and the sample result, so a full scan can be run in batches.
     */
    suspend fun validateAll(
        groups: List<CatalogGroup>,
        repository: MovieBoxRepository,
        limit: Int = 10,
    ): ValidationSummary {
        val results = groups.take(limit).map { validateGroup(it, repository) }
        return ValidationSummary(
            groups = groups.size,
            records = groups.sumOf { it.records.size },
            clean = results.count { it.issues.isEmpty() },
            warnings = results.count { it.issues.isNotEmpty() },
            results = results,
        )
    }
}