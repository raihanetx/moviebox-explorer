package com.example.movieboxexplorer.data

/**
 * Country-exclusion policy for every listing surface the app renders.
 *
 * The catalogue tags every record with a `countryName` and the listing endpoints
 * carry it reliably, so listings can be filtered client-side — the server only
 * supports inclusive `country=` selection, not exclusion.
 *
 *  * **Nigeria** is hidden everywhere (home rails, grids, tabs, ranking, search,
 *    related titles, suggestions, banner and ranked items inside sections).
 *  * **India** is hidden only in the animation listings (`channelId == 1006`,
 *    the `/web/animated-series` grid). Everywhere else Indian titles stay.
 *
 * A title with a **blank** `countryName` is always kept — we never hide unproven
 * data. The policy is applied to listing payloads *before* they reach the UI or
 * the caches, so filtered-out titles never flash into view and never resurface
 * from stale cache entries.
 */
object ContentPolicy {

    /** `1006` — the channel the website's animation grid lists. */
    const val ANIME_CHANNEL_ID: Int = 1006

    /** Lowercases and strips everything but a–z, so `"Nigeria."` / `"(Nigeria)"` / `"NG-Nigeria"` match. */
    fun normalize(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z]+"), " ").trim()

    private fun hasToken(countryName: String, token: String): Boolean =
        normalize(countryName).split(' ').contains(token)

    /** True when the record is tagged Nigeria. */
    fun isNigeria(countryName: String): Boolean = hasToken(countryName, "nigeria")

    /** True when the record is tagged India. */
    fun isIndia(countryName: String): Boolean = hasToken(countryName, "india")

    /** A record is hidden everywhere when it is tagged Nigeria. */
    fun hiddenEverywhere(item: SubjectBrief): Boolean = isNigeria(item.countryName)

    /** In the animation listings, India and Nigeria are both hidden. */
    fun hiddenInAnimation(item: SubjectBrief): Boolean =
        hiddenEverywhere(item) || isIndia(item.countryName)

    /** Filters a plain listing, e.g. a grid or a search result page. */
    fun keep(items: List<SubjectBrief>, animation: Boolean = false): List<SubjectBrief> =
        items.filter { !(if (animation) hiddenInAnimation(it) else hiddenEverywhere(it)) }

    /** True when an embedded banner subject must be dropped. */
    private fun hidden(subject: SubjectBrief, animation: Boolean): Boolean =
        if (animation) hiddenInAnimation(subject) else hiddenEverywhere(subject)

    /**
     * Filters one home/catalog section. Returns `null` when nothing displayable
     * is left — an entirely-Nigerian section (e.g. `Nollywood Movie`) disappears
     * instead of rendering empty.
     */
    fun filterSection(section: OperatingSection, animation: Boolean = false): OperatingSection? {
        return section.copy(
            subjects = keep(section.subjects, animation),
            rankingSubjectList = keep(section.rankingSubjectList, animation),
            banner = section.banner?.let { banner ->
                banner.copy(items = banner.items.filter { item ->
                    item.subject?.let { !hidden(it, animation) } ?: true
                })
            },
        ).takeIf { kept ->
            kept.subjects.isNotEmpty() ||
                kept.rankingSubjectList.isNotEmpty() ||
                kept.banner?.items?.isNotEmpty() == true
        }
    }

    /** True when a filter-bar value must be hidden (value `id` is a country name). */
    fun hiddenFilterValue(id: String, animation: Boolean = false): Boolean =
        isNigeria(id) || (animation && isIndia(id))

    /**
     * Filter-bar helper — the page exposes every country the grid can be narrowed
     * to. The excluded countries are removed so a user cannot select them and end
     * up on an empty page: Nigeria everywhere, India in the animation grid only.
     */
    fun hideFilterValues(configs: List<FilterConfig>, animation: Boolean = false): List<FilterConfig> =
        configs.map { config ->
            config.copy(
                items = config.items.map { group ->
                    group.copy(
                        filterVals = group.filterVals.filterNot { hiddenFilterValue(it.id, animation) },
                    )
                },
            )
        }
}
