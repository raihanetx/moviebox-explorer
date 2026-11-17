package com.example.movieboxexplorer.smoke

import com.example.movieboxexplorer.data.SubjectBrief

internal data class CatalogStats(
    var total: Int = 0,
    var noTitle: Int = 0,
    var noCover: Int = 0,
    var noDetailPath: Int = 0,
    var noReleaseDate: Int = 0,
    var noGenre: Int = 0,
    var noRating: Int = 0,
) {
    fun add(subject: SubjectBrief) {
        total++
        if (subject.title.isBlank()) noTitle++
        if (subject.cover?.url.isNullOrBlank()) noCover++
        if (subject.detailPath.isBlank()) noDetailPath++
        if (subject.releaseDate.isBlank()) noReleaseDate++
        if (subject.genre.isBlank()) noGenre++
        if (subject.imdbRatingValue.isBlank()) noRating++
    }

    fun print(name: String) {
        fun pct(value: Int): String =
            if (total == 0) "0.0" else "%.1f".format(100.0 * value / total)

        println(
            "$name: $total records | missing title=$noTitle cover=$noCover(${pct(noCover)}%) " +
                "detailPath=$noDetailPath(${pct(noDetailPath)}%) date=$noReleaseDate " +
                "genre=$noGenre rating=$noRating",
        )
    }
}
