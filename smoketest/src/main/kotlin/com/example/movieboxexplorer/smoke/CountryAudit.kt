package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.home
import com.example.movieboxexplorer.data.filter
import com.example.movieboxexplorer.data.FilterRequest
import kotlinx.serialization.json.Json

/**
 * Live measurement of where country-tagged items live, so the geo-block
 * decision can be designed from data rather than assumed.
 *
 * Usage: gradle :smoketest:run --args="country-audit"
 */
suspend fun cmd_country_audit(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {
    println("=== home rails: country distribution ===")
    val page = api.home("themoviebox.xyz", useSession = true)
    var all = 0
    val byRail = mutableMapOf<String, MutableMap<String, Int>>()
    for (sec in page.operatingList) {
        for (it in sec.subjects) {
            all++
            val c = it.countryName.ifBlank { "(blank)" }
            byRail.getOrPut(sec.title) { mutableMapOf() }.merge(c, 1, Int::plus)
        }
    }
    println("total items: $all")
    val totals = byRail.values.flatMap { it.entries }.groupingBy { it.key }.fold(0) { a, e -> a + e.value }
    totals.entries.sortedByDescending { it.value }.forEach { println("  ${it.key.padEnd(18)} ${it.value}") }
    println("\nrails containing Nigeria:")
    byRail.forEach { (rail, m) ->
        val n = m["Nigeria"] ?: 0
        if (n > 0) println("  $rail: $n Nigeria (total ${m.values.sum()})")
    }
    println("\nrails containing India:")
    byRail.forEach { (rail, m) ->
        val n = m["India"] ?: 0
        if (n > 0) println("  $rail: $n India (total ${m.values.sum()})")
    }

    println("\n=== filter API: total counts ===")
    for ((channel, name) in listOf(1 to "Movie/films", 2 to "TV/Series", 1006 to "Anime")) {
        for (country in listOf("Nigeria", "India")) {
            val page1 = api.filter(FilterRequest(page = 1, perPage = 1, channelId = channel, country = country))
            val total = page1.pager?.totalCount ?: -1
            println("channel=$channel ($name) country=$country -> totalCount=$total")
        }
        // sample what's in the unfiltered catalog: first 28 items of page 1
        val p1 = api.filter(FilterRequest(page = 1, perPage = 28, channelId = channel))
        val c = p1.items.groupingBy { it.countryName.ifBlank { "(blank)" } }.eachCount()
        println("channel=$channel page1 sample: $c (hasMore=${p1.pager?.hasMore})")
    }

    api.close()
    return
}

suspend fun cmd_country_audit2(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: MovieBoxRepository,
) {
    for (page in 1..3) {
        val p = api.filter(FilterRequest(page = page, perPage = 28, channelId = 1006, country = "India"))
        println("anime country=India page=$page -> items=${p.items.size}")
        p.items.take(8).forEach { println("   ${it.title} | ${it.countryName}") }
        p.items.forEach { check(it.countryName == "India") { "server ignored country filter! ${it.countryName}" } }
    }
    // presence density in unfiltered pages
    for (page in 1..6) {
        val p = api.filter(FilterRequest(page = page, perPage = 28, channelId = 1006))
        val c = p.items.groupingBy { it.countryName.ifBlank { "(blank)" } }.eachCount()
        println("anime unfiltered page=$page -> $c")
    }
    api.close()
    return
}
