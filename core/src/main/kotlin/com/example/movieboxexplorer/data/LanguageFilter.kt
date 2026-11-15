package com.example.movieboxexplorer.data

/**
 * The language picker rule.
 *
 * Show:
 *  - the original audio track;
 *  - an English dub, if the catalogue has one;
 *  - a Hindi dub, if the catalogue has one.
 *
 * Everything else — subtitles and other language dubs — is hidden.
 */
object LanguageFilter {

    fun visible(languages: List<LanguageInfo>): List<LanguageInfo> =
        languages.filter { language ->
            language.isOriginal ||
                (!language.isSubtitle &&
                    (language.code.equals("en", true) || language.code.equals("hi", true)))
        }
}