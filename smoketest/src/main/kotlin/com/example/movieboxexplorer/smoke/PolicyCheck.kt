package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.ContentPolicy
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.home

/**
 * Live verification of the content-exclusion policy.
 *
 * Usage: gradle :smoketest:run --args="policy-check"
 */
suspend fun cmd_policy_check(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {
    val host = "themoviebox.xyz"
    var failures = 0
    fun check(label: String, ok: Boolean, detail: String) {
        println((if (ok) "PASS" else "FAIL") + " $label  $detail")
        if (!ok) failures++
    }

    // --- home: raw vs filtered ---
    val raw = api.home(host, useSession = false)
    val filteredSections = raw.operatingList.mapNotNull { ContentPolicy.filterSection(it) }
    val rawItems = raw.operatingList.flatMap { it.subjects }
    val keptItems = filteredSections.flatMap { it.subjects }
    val rawNg = rawItems.count { ContentPolicy.isNigeria(it.countryName) }
    val keptNg = keptItems.count { ContentPolicy.isNigeria(it.countryName) }
    val droppedRails = raw.operatingList.size - filteredSections.size
    check("home Nigeria removed", keptNg == 0,
        "$rawNg Nigeria items in ${raw.operatingList.size} rails -> $keptNg in ${filteredSections.size} rails ($droppedRails rails dropped)")
    check("home blank kept", keptItems.count { it.countryName.isBlank() } >= rawItems.count { it.countryName.isBlank() },
        "blank-country items kept: ${keptItems.count { it.countryName.isBlank() }}")
    val nolly = filteredSections.firstOrNull { it.title.contains("Nollywood", true) }
    val nollyNg = nolly?.subjects?.count { ContentPolicy.isNigeria(it.countryName) } ?: 0
    check("Nollywood rail Nigeria-free", nollyNg == 0,
        nolly?.subjects?.joinToString { it.title + " | " + it.countryName } ?: "rail dropped entirely")

    // --- animated grid (pages 1..3): India & Nigeria gone ---
    for (page in 1..3) {
        val grid = repository.animatedGrid(host, page = page)
        val bad = grid.items.count { ContentPolicy.isIndia(it.countryName) || ContentPolicy.isNigeria(it.countryName) }
        check("anime page $page", bad == 0, "${grid.items.size} items, $bad India/Nigeria")
    }

    // --- films grid: Nigeria gone, India stays ---
    val films = repository.filmGrid(host, 1)
    val filmsNg = films.items.count { ContentPolicy.isNigeria(it.countryName) }
    val filmsIn = films.items.count { ContentPolicy.isIndia(it.countryName) }
    check("films Nigeria removed", filmsNg == 0, "${films.items.size} items, $filmsNg Nigeria, $filmsIn India (India kept)")

    // --- TV grid: Nigeria gone, India stays ---
    val tv = repository.seriesGrid(host, 1)
    val tvNg = tv.items.count { ContentPolicy.isNigeria(it.countryName) }
    check("tv Nigeria removed", tvNg == 0, "${tv.items.size} items, $tvNg Nigeria")

    // --- search: no Nigeria ---
    val results = repository.search("Black Torch", subjectType = 2)
    val searchNg = results.count { ContentPolicy.isNigeria(it.countryName) }
    check("search Nigeria removed", searchNg == 0, "${results.size} results, $searchNg Nigeria")

    // --- filter bar: Nigeria hidden everywhere; India hidden in anime only ---
    for ((path, label, expectIndiaHidden) in listOf(
        Triple("/web/film", "films", false),
        Triple("/web/tv-series", "tv", false),
        Triple("/web/animated-series", "anime", true),
    )) {
        val configs = repository.filterConfig(path)
        if (configs.isEmpty()) {
            println("WARN $label filter config unavailable from mirrors")
            continue
        }
        val countryGroup = configs
            .flatMap { it.items }
            .firstOrNull { it.filterType.equals("country", ignoreCase = true) }
        val ids = countryGroup?.filterVals?.map { it.id } ?: emptyList()
        val ngGone = ids.none { ContentPolicy.isNigeria(it) }
        val inHidden = ids.none { ContentPolicy.isIndia(it) }
        check("$label filter Nigeria hidden", ngGone, "country values: $ids")
        if (expectIndiaHidden) {
            check("anime filter India hidden", inHidden, "")
        } else {
            check("$label filter India kept", !inHidden, "")
        }
    }

    println("failures: $failures")
    api.close()
    return
}
