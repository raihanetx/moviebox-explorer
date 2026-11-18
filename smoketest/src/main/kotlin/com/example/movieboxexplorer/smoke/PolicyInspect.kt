package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.ContentPolicy
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.home

suspend fun cmd_policy_inspect(
    keyword: String,
    explicitPath: String?,
    args: Array<String>,
    api: MovieBoxApi,
    repository: com.example.movieboxexplorer.data.MovieBoxRepository,
) {
    val raw = api.home("themoviebox.xyz", useSession = false)
    println("=== dropped sections ===")
    for (sec in raw.operatingList) {
        val kept = ContentPolicy.filterSection(sec)
        if (kept == null) {
            println("DROP: ${sec.title} | type=${sec.type} | subjects=${sec.subjects.size} | ranking=${sec.rankingSubjectList.size} | banner=${sec.banner?.items?.size ?: 0} | opId=${sec.opId}")
        }
    }
    for (sec in raw.operatingList) {
        if (!sec.title.contains("Nollywood", true) && !sec.title.contains("Africa", true) && !sec.title.contains("Must-watch", true)) continue
        println("=== ${sec.title} (${sec.type}) raw subjects=${sec.subjects.size} bannerItems=${sec.banner?.items?.size ?: 0}")
        sec.subjects.forEach { println("   subj: ${it.title} | ${it.countryName}") }
        sec.banner?.items?.forEach { println("   banner: ${it.title} | subject=${it.subject?.let { s -> s.title + " | " + s.countryName } ?: "no-subject"}") }
    }
    api.close()
    return
}
