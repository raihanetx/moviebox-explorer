package com.example.movieboxexplorer.data

/**
 * Pure season/episode maths.
 *
 * Kept separate from [MovieBoxRepository] so the exact logic can be unit-tested on
 * the JVM without a network or an Android device.
 */
object SeasonMath {

    /**
     * Season breakdown of a resource.
     *
     * The API can deliver one season as several entries (a split cours, or a
     * corrected upload): for example season 2 as `maxEp=4` and `maxEp=20`. The UI
     * needs one row per season with the full count, so entries are grouped by season
     * number.
     *
     * The episode count is the largest of:
     *
     *  - the summed `maxEp` of the entries (with the 24 + 48 → 48 guard below);
     *  - the number of distinct episode numbers in `allEp`;
     *  - the largest `resolutions[].epNum`.
     *
     * When two entries look like a split season (`4` + `20`), their counts are
     * summed. When one is exactly double the other and the smaller is a plausible
     * full season (`24` + `48`), the larger wins — that is a combined record, not two
     * parts.
     */
    fun seasonsOf(resource: Resource?): List<SeasonInfo> {
        val grouped = resource?.seasons.orEmpty().groupBy { it.se }
        return grouped.entries
            .sortedBy { it.key }
            .map { (seasonNumber, entries) ->
                val maxEpValues = entries
                    .map { it.maxEp }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()

                val summedMaxEp = when {
                    maxEpValues.size <= 1 -> maxEpValues.firstOrNull() ?: 0
                    maxEpValues.size == 2 &&
                        maxEpValues[1] == 2 * maxEpValues[0] &&
                        maxEpValues[0] >= 12 -> maxEpValues[1]
                    else -> maxEpValues.sum()
                }

                val allEpCount = entries
                    .flatMap { season ->
                        season.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                    }
                    .toSet()
                    .size

                val resolutionMax = entries
                    .flatMap { it.resolutions }
                    .maxOfOrNull { it.epNum } ?: 0

                val episodes = maxOf(summedMaxEp, allEpCount, resolutionMax)

                val resolutions = entries
                    .flatMap { it.resolutions }
                    .groupBy { it.resolution }
                    .map { (resolution, list) ->
                        Resolution(resolution, list.maxOf { it.epNum })
                    }
                    .sortedByDescending { it.resolution }

                SeasonInfo(
                    season = seasonNumber,
                    episodes = episodes,
                    resolutions = resolutions,
                )
            }
    }

    /**
     * Merge an H5 resource with a mobile `season-info` resource.
     *
     * For every season number, the merged episode count is the larger of the two.
     * `allEp` lists are unioned and resolutions are merged by taking the largest
     * `epNum` per quality. This fixes the case where H5 reports a partial season
     * (4 episodes) while the mobile API reports the full upload (24/48).
     */
    fun mergeResources(primary: Resource?, secondary: Resource?): Resource? {
        if (primary == null) return secondary
        if (secondary == null) return primary

        val seasons = (primary.seasons + secondary.seasons)
            .groupBy { it.se }
            .map { (seasonNumber, entries) ->
                val maxEp = entries.maxOf { it.maxEp }
                val allEp = entries
                    .map { it.allEp }
                    .filter { it.isNotBlank() }
                    .joinToString(",")
                val resolutions = entries
                    .flatMap { it.resolutions }
                    .groupBy { it.resolution }
                    .map { (resolution, list) ->
                        Resolution(resolution, list.maxOf { it.epNum })
                    }
                    .sortedByDescending { it.resolution }
                Season(
                    se = seasonNumber,
                    maxEp = maxEp,
                    allEp = allEp,
                    resolutions = resolutions,
                )
            }
            .sortedBy { it.se }

        return primary.copy(
            seasons = seasons,
            source = primary.source.ifBlank { secondary.source },
            uploadBy = primary.uploadBy.ifBlank { secondary.uploadBy },
        )
    }

    /** Quality levels summed across every season, highest first. */
    fun mergeResolutions(seasons: List<SeasonInfo>): List<Resolution> =
        seasons.flatMap { it.resolutions }
            .groupBy { it.resolution }
            .map { (resolution, entries) -> Resolution(resolution, entries.sumOf { it.epNum }) }
            .sortedByDescending { it.resolution }
}