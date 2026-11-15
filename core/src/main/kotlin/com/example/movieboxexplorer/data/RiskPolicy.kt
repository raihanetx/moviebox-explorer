package com.example.movieboxexplorer.data

/**
 * How the platform flags a title.
 *
 * The flags are **independent** and mean different things, which is why they are kept
 * apart instead of being collapsed into one boolean:
 *
 *  * [RiskLevel.Unlisted] — `webHighRisk`. The web UI hides the title and pushes the
 *    visitor to the mobile app. It is a *visibility / takedown risk* flag: a recent
 *    2026 anime release can carry it while being completely family friendly.
 *  * [RiskLevel.Adult] — `isPornographic`. Adult content. Only the **detail** record
 *    carries this; every listing endpoint reports `false`, so it can only be known
 *    after a title has been opened.
 *  * [RiskLevel.Forbidden] — `isForbid`. The record exists but must not be played.
 */
enum class RiskLevel { Normal, Unlisted, Adult, Forbidden }

/**
 * The three risk flags of a title, as read from the catalogue.
 *
 * A title can carry more than one: an unlisted adult title is both
 * [RiskLevel.Unlisted] and [RiskLevel.Adult].
 */
data class RiskFlags(
    /** `webHighRisk` — hidden from the web UI, app-only. */
    val unlisted: Boolean = false,
    /** `isPornographic` — adult content. */
    val adult: Boolean = false,
    /** `isForbid` — the record must not be played. */
    val forbidden: Boolean = false,
) {
    /** The strongest flag, in the order the UI should react to it. */
    val level: RiskLevel
        get() = when {
            forbidden -> RiskLevel.Forbidden
            adult -> RiskLevel.Adult
            unlisted -> RiskLevel.Unlisted
            else -> RiskLevel.Normal
        }

    /** Forbidden titles must not be shown as playable at all. */
    val blocked: Boolean get() = forbidden

    /** True when the title needs the age gate before it may be opened. */
    val needsAgeGate: Boolean get() = adult

    companion object {
        val NONE = RiskFlags()

        /** Builds the flags of a title from the fields the API exposes. */
        fun of(webHighRisk: Boolean, isPornographic: Boolean, isForbid: Boolean = false) =
            RiskFlags(
                unlisted = webHighRisk,
                adult = isPornographic,
                forbidden = isForbid,
            )
    }
}
