package com.example.movieboxexplorer.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runBlocking

suspend fun MovieBoxRepository.loadTitle(detailPath: String, subjectId: String): TitleInfo? {
    // H5 is the fast path. Only call the mobile API when H5 is missing an
    // English/Hindi dub or has no season data — otherwise the mobile round-trip
    // makes every detail page feel slow for no benefit.
    val h5Main = api.detail(detailPath, subjectId)
    var main = h5Main
    if (main == null || main.subject?.webHighRisk == true) {
        val mobileDetail = mobile.detail(subjectId)?.toTitleDetail()
        if (mobileDetail?.subject != null) main = mobileDetail
    }
    val resolvedMain = main ?: return null
    var subject = resolvedMain.subject ?: return null

    val h5HasEnglish = subject.dubs.any { it.type == 0 && it.lanCode.equals("en", true) }
    val h5HasHindi = subject.dubs.any { it.type == 0 && it.lanCode.equals("hi", true) }
    val h5SeasonEpisodes = SeasonMath.seasonsOf(resolvedMain.resource).sumOf { it.episodes }

    val needsMobileDubs = subject.dubs.isEmpty() || !h5HasEnglish || !h5HasHindi
    val needsMobileSeasons = resolvedMain.resource == null || h5SeasonEpisodes == 0

    val mobileDubs: List<MobileDub>
    val mobileMainSeasons: Resource?
    if (needsMobileDubs || needsMobileSeasons) {
        val pair = coroutineScope {
            val dubs = async { if (needsMobileDubs) mobile.dubInfo(subjectId) else emptyList() }
            val seasons = async { if (needsMobileSeasons) cachedSeasonInfo(subjectId) else null }
            dubs.await() to seasons.await()
        }
        mobileDubs = pair.first
        mobileMainSeasons = pair.second
    } else {
        mobileDubs = emptyList()
        mobileMainSeasons = null
    }

    // The H5 record's dub list can be partial (for example, a missing Hindi
    // track). The mobile `dub-info` endpoint is the fuller index, so merge it
    // in. A dub with a non-empty detailPath wins over one without.
    val mergedDubs = (subject.dubs + mobileDubs.map { it.toDubVariant() })
        .filter { it.subjectId.isNotBlank() || it.detailPath.isNotBlank() }
        .groupBy { it.subjectId.ifBlank { it.detailPath } }
        .map { (_, variants) -> variants.firstOrNull { it.detailPath.isNotBlank() } ?: variants.first() }
    if (mergedDubs.isNotEmpty()) subject = subject.copy(dubs = mergedDubs)

    // The parent record's mobile `season-info` is the reference for how many
    // episodes a complete track has. If a dub's H5 resource is shorter than
    // that, fetch the dub's own mobile `season-info` and merge it in.
    val mainResource = SeasonMath.mergeResources(
        resolvedMain.resource,
        mobileMainSeasons,
    )
    val ownSeasons = SeasonMath.seasonsOf(mainResource)
    val referenceEpisodeCount = ownSeasons.sumOf { it.episodes }

    val gate = Semaphore(20)
    val languages = coroutineScope {
        subject.dubs.map { variant ->
            async {
                // H5 detail is the primary source; mobile season-info is only fired
                // when H5 returns nothing usable. Both run in the same round so
                // there is no second wave even if the mobile endpoint is slow.
                val h5Deferred = async {
                    gate.withPermit {
                        api.detail(variant.detailPath, variant.subjectId)
                            ?: mobile.detail(variant.subjectId)?.toTitleDetail()
                    }
                }
                val h5 = h5Deferred.await()
                val h5Resource = h5?.resource
                val h5EpisodeCount = SeasonMath.seasonsOf(h5Resource).sumOf { it.episodes }
                val mobileResource = if (
                    h5Resource == null ||
                    (referenceEpisodeCount > 0 && h5EpisodeCount < referenceEpisodeCount)
                ) {
                    gate.withPermit { cachedSeasonInfo(variant.subjectId) }
                } else {
                    null
                }
                val resource = SeasonMath.mergeResources(h5Resource, mobileResource)
                val seasons = SeasonMath.seasonsOf(resource)
                LanguageInfo(
                    label = variant.lanName.ifBlank { variant.lanCode },
                    code = variant.lanCode,
                    kind = if (variant.type == 1) "Subtitle" else "Dub",
                    isOriginal = variant.original,
                    episodes = seasons.sumOf { it.episodes },
                    seasons = seasons,
                    resolutions = SeasonMath.mergeResolutions(seasons),
                    uploader = resource?.uploadBy.orEmpty(),
                    detailPath = variant.detailPath,
                    subjectId = variant.subjectId,
                    isPornographic = h5?.subject?.isPornographic == true,
                )
            }
        }.awaitAll()
    }

    val normalizedLanguages = SeasonMath.normalizeLanguages(ownSeasons, languages)

    val reference = maxOf(
        referenceEpisodeCount,
        normalizedLanguages.maxOfOrNull { it.episodes } ?: 0,
    )
    val referenceSeasons = maxOf(
        ownSeasons.count { it.episodes > 0 },
        normalizedLanguages.maxOfOrNull { it.seasonCount } ?: 0,
    )

    // A title without language variants — most movies — is its own track, so the
    // picker still has something to play.
    val tracks = normalizedLanguages.ifEmpty {
        listOf(
            LanguageInfo(
                label = "Original",
                code = "",
                kind = "Dub",
                isOriginal = true,
                episodes = ownSeasons.sumOf { it.episodes },
                seasons = ownSeasons,
                resolutions = SeasonMath.mergeResolutions(ownSeasons),
                uploader = mainResource?.uploadBy.orEmpty(),
                detailPath = detailPath,
                subjectId = subjectId,
                isPornographic = subject.isPornographic,
            )
        )
    }

    // The cast is optional. Fetching it from the mobile API adds a network call
    // to every detail open, so only the H5 record's cast is used.
    val stars = resolvedMain.stars

    return TitleInfo(
        subject = subject,
        stars = stars.filter { it.name.isNotBlank() },
        languages = tracks.sortedWith(
            compareByDescending<LanguageInfo> { it.episodes }.thenByDescending { it.seasonCount }
        ),
        referenceEpisodes = reference,
        referenceSeasons = referenceSeasons,
        isForbidden = resolvedMain.isForbid,
        resource = mainResource,
    )
}


