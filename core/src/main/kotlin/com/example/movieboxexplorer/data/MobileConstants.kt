package com.example.movieboxexplorer.data

/** Endpoint prefixes shared by the mobile client and its catalog calls. */
internal object MobileConstants {
    const val MOBILE_PREFIX = "/wefeed-mobile-bff"
    const val BOOTSTRAP_PATH = "$MOBILE_PREFIX/tab/ranking-list"
    val BOOTSTRAP_QUERY = mapOf(
        "tabId" to "0",
        "categoryType" to "4516404531735022304",
        "page" to "1",
        "perPage" to "1",
    )

    /** The guide names one path; some builds expose the list under another. */
    val RECOMMEND_PATHS = listOf(
        "$MOBILE_PREFIX/subject-api/recommend",
        "$MOBILE_PREFIX/subject-api/recommend/list",
        "$MOBILE_PREFIX/subject-api/recommend-rec",
    )
}
