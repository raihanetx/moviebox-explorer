package com.example.movieboxexplorer.data

/**
 * One season of a language track.
 *
 * @param episodes episodes present in this season
 * @param resolutions how many episodes exist at each quality level
 */
data class SeasonInfo(
    val season: Int,
    val episodes: Int,
    val resolutions: List<Resolution>,
)

/**
 * A language track of a title, resolved against the live catalogue.
 *
 * @param episodes total episodes across every season
 * @param seasons per-season breakdown
 * @param resolutions quality levels aggregated over all seasons
 */
data class LanguageInfo(
    val label: String,
    val code: String,
    val kind: String,
    val isOriginal: Boolean,
    val episodes: Int,
    val seasons: List<SeasonInfo>,
    val resolutions: List<Resolution>,
    val uploader: String,
    val detailPath: String,
    val subjectId: String,
    /**
     * Adult-content flag of this language track. Only the detail record carries it,
     * and the track is fetched for its episode list anyway, so it comes for free.
     */
    val isPornographic: Boolean = false,
) {
    val isSubtitle: Boolean get() = kind.equals("Subtitle", ignoreCase = true)

    /** How many seasons this track actually covers (0 for a movie). */
    val seasonCount: Int get() = seasons.count { it.episodes > 0 }

    /** Risk flags of this track. */
    val risk: RiskFlags get() = RiskFlags(adult = isPornographic)
}

/**
 * The filter state of a catalog grid.
 *
 * The website keeps these in the URL query and the grid's "load more" repeats them
 * back on every request. Every value is optional — the API rejects a body that
 * carries an empty string, so an unset filter has to be omitted entirely.
 */
data class CatalogFilter(
    val genre: String? = null,
    val country: String? = null,
    val year: String? = null,
    val sort: String? = null,
    val rate: String? = null,
    val classify: String? = null,
) {
    val isEmpty: Boolean
        get() = genre == null && country == null && year == null &&
            sort == null && rate == null && classify == null
}

/** One horizontal row on a catalog screen. */
data class CatalogRow(
    val title: String,
    val items: List<SubjectBrief>,
)

/**
 * A catalog page the website renders as a flat poster grid.
 *
 * The website's TV and animation pages are grids rather than rows of carousels, and
 * they are paginated — the payload carries the same pager shape the API uses.
 */
data class CatalogGrid(
    val items: List<SubjectBrief>,
    val hasMore: Boolean = false,
    val nextPage: Int = 2,
)

/**
 * The content of a catalog screen.
 *
 * The website renders some catalogs as rows of carousels and others as a flat poster
 * grid; the app copies whichever the matching page of the website uses.
 */
sealed interface CatalogContent {
    data class Rows(val rows: List<CatalogRow>) : CatalogContent
    data class Grid(val grid: CatalogGrid) : CatalogContent
}

/** Everything the detail screen renders. */
data class TitleInfo(
    val subject: Subject,
    val stars: List<Staff>,
    val languages: List<LanguageInfo>,
    /** Highest episode count across all languages — the yardstick for "complete". */
    val referenceEpisodes: Int,
    /** Highest season count across all languages. */
    val referenceSeasons: Int,
    /** `isForbid` — the record exists but must not be played. */
    val isForbidden: Boolean = false,
    /** The raw resource block from the detail record, shown in the info sheet. */
    val resource: Resource? = null,
) {
    val isMovie: Boolean get() = subject.subjectType == 1

    /**
     * The risk flags of this title.
     *
     * `webHighRisk` marks a title the web UI hides, `isPornographic` marks adult
     * content. A title can carry both, and a language track can be adult even when
     * the parent record is not — so the track-level flag is folded in as well.
     */
    val risk: RiskFlags
        get() = RiskFlags(
            unlisted = subject.webHighRisk,
            adult = subject.isPornographic || languages.any { it.isPornographic },
            forbidden = isForbidden,
        )

    fun missingEpisodes(info: LanguageInfo): Int =
        (referenceEpisodes - info.episodes).coerceAtLeast(0)

    fun missingSeasons(info: LanguageInfo): Int =
        (referenceSeasons - info.seasonCount).coerceAtLeast(0)
}
