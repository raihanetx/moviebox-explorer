package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.loadTitle
import com.example.movieboxexplorer.data.close
import kotlinx.coroutines.runBlocking

/**
 * Runs the exact networking + parsing code the Android app uses against the live
 * catalogue, so the data layer can be verified without a device.
 *
 * Commands live in the `smoke/` group files (`*Smoke.kt`); each is a `cmd_*`
 * function taking (keyword, explicitPath, args, api, repository). The default
 * branch is the title-detail dump for a keyword.
 *
 * Usage: gradle :smoketest:run --args="attack on titan"
 */
fun main(args: Array<String>) = runBlocking {
    val keyword = args.firstOrNull() ?: "black torch"
    val explicitPath = args.getOrNull(1)
    val api = MovieBoxApi()
    val repository = MovieBoxRepository(api)

    when (keyword) {
        "diagnostics" -> cmd_diagnostics(keyword, explicitPath, args, api, repository)
        "home-parts" -> cmd_home_parts(keyword, explicitPath, args, api, repository)
        "timing" -> cmd_timing(keyword, explicitPath, args, api, repository)
        "country-audit" -> cmd_country_audit(keyword, explicitPath, args, api, repository)
        "policy-check" -> cmd_policy_check(keyword, explicitPath, args, api, repository)
        "policy-inspect" -> cmd_policy_inspect(keyword, explicitPath, args, api, repository)
        "country-audit2" -> cmd_country_audit2(keyword, explicitPath, args, api, repository)
        "home-time" -> cmd_home_time(keyword, explicitPath, args, api, repository)
        "mobile" -> cmd_mobile(keyword, explicitPath, args, api, repository)
        "titles" -> cmd_titles(keyword, explicitPath, args, api, repository)
        "ssr" -> cmd_ssr(keyword, explicitPath, args, api, repository)
        "ssrsearch" -> cmd_ssrsearch(keyword, explicitPath, args, api, repository)
        "homessr" -> cmd_homessr(keyword, explicitPath, args, api, repository)
        "rank" -> cmd_rank(keyword, explicitPath, args, api, repository)
        "home" -> cmd_home(keyword, explicitPath, args, api, repository)
        "catalog" -> cmd_catalog(keyword, explicitPath, args, api, repository)
        "best" -> cmd_best(keyword, explicitPath, args, api, repository)
        "films" -> cmd_films(keyword, explicitPath, args, api, repository)
        "page-op" -> cmd_page_op(keyword, explicitPath, args, api, repository)
        "search-inspect" -> cmd_search_inspect(keyword, explicitPath, args, api, repository)
        "audit-catalog" -> cmd_audit_catalog(keyword, explicitPath, args, api, repository)
        "sweep" -> cmd_sweep(keyword, explicitPath, args, api, repository)
        "validate-title" -> cmd_validate_title(keyword, explicitPath, args, api, repository)
        "validate-catalog" -> cmd_validate_catalog(keyword, explicitPath, args, api, repository)
        "filters" -> cmd_filters(keyword, explicitPath, args, api, repository)
        "filters2" -> cmd_filters2(keyword, explicitPath, args, api, repository)
        "filter" -> cmd_filter(keyword, explicitPath, args, api, repository)
        "grid" -> cmd_grid(keyword, explicitPath, args, api, repository)
        "website" -> cmd_website(keyword, explicitPath, args, api, repository)
        "risk" -> cmd_risk(keyword, explicitPath, args, api, repository)
        "play" -> cmd_play(keyword, explicitPath, args, api, repository)
        else -> {
            var detailPath = explicitPath
            var subjectId = ""

            if (detailPath == null) {
                println("=== search: \"$keyword\" ===")
                val results = repository.search(keyword, subjectType = 2)
                if (results.isEmpty()) {
                    println("  (no series results)")
                }
                results.take(8).forEach {
                    println("  ${it.title} | ${it.releaseDate} | ${it.genre} | ${it.detailPath}")
                }
                val target = results.firstOrNull { it.detailPath.isNotBlank() } ?: results.firstOrNull()
                if (target == null) {
                    println("no title found, aborting")
                    api.close()
                    return@runBlocking
                }
                detailPath = target.detailPath
                subjectId = target.subjectId
                println()
                println("=== detail: ${target.title} (${target.detailPath}) ===")
            } else {
                println("=== detail: direct path $detailPath ===")
            }

            val info = repository.loadTitle(detailPath, subjectId)
            if (info == null) {
                println("  could not resolve title")
                api.close()
                return@runBlocking
            }

            println("  year      : ${info.subject.releaseDate}")
            println("  genre     : ${info.subject.genre}")
            println("  country   : ${info.subject.countryName}")
            println("  imdb      : ${info.subject.imdbRatingValue} (${info.subject.imdbRatingCount})")
            println("  unlisted  : ${info.subject.webHighRisk}")
            println("  adult     : ${info.subject.isPornographic}")
            println("  forbidden : ${info.isForbidden}")
            println("  risk      : ${info.risk.level}")
            println("  reference : ${info.referenceEpisodes} episodes / ${info.referenceSeasons} season(s)")
            println("  languages :")
            info.languages.forEach { language ->
                val missing = info.missingEpisodes(language)
                val missingSeasons = info.missingSeasons(language)
                val status = buildList {
                    if (missing > 0) add("missing $missing ep")
                    if (missingSeasons > 0) add("missing $missingSeasons season")
                }.joinToString(", ").ifEmpty { "complete" }
                val seasons = language.seasons
                    .filter { it.episodes > 0 }
                    .joinToString(" ") { "S${it.season}:${it.episodes}" }
                val resolutions = language.resolutions.joinToString(", ") { "${it.resolution}p x${it.epNum}" }
                println(
                    "    ${language.label.padEnd(18)} ${language.kind.padEnd(10)} " +
                        "eps=${language.episodes.toString().padEnd(3)} seasons=${language.seasonCount} $status" +
                        (if (language.isPornographic) " ADULT" else "") +
                        (if (seasons.isNotBlank()) "  [$seasons]" else "") +
                        (if (resolutions.isNotBlank()) "  [$resolutions]" else "")
                )
            }
            println("  cast      : ${info.stars.size} entries")
        }
    }

    api.close()
}
