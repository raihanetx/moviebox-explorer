package com.example.movieboxexplorer.data

/**
 * A group of catalogue records that belong to the same title/franchise.
 *
 * The API often returns the same title several times: the original, a language
 * variant, a season split, or a duplicate upload. This groups them so the UI can
 * show one title and merge the data.
 */
data class CatalogGroup(
    val key: String,
    val title: String,
    val records: List<SubjectBrief>,
    val canonical: SubjectBrief,
)