package com.example.movieboxexplorer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The mobile BFF wraps every search result in a `subject` object, while other
 * endpoints return the subject fields directly. The item is kept as JSON so both
 * shapes can be decoded without two near-identical models.
 */
@Serializable
data class MobileSearchPage(
    val items: List<JsonElement> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class MobileSearchRequest(
    val keyword: String,
    val page: Int = 1,
    val perPage: Int = 20,
)

/**
 * A language variant as the mobile `get` / `dub-info` endpoints report it.
 *
 * Some app versions call the language code `lanCode`, others `language`; both are
 * accepted and folded into [DubVariant.lanCode].
 */
@Serializable
data class MobileDub(
    val subjectId: String = "",
    val lanName: String = "",
    val lanCode: String = "",
    val language: String = "",
    val original: Boolean = false,
    val type: Int = 0,
    val detailPath: String = "",
) {
    fun toDubVariant(): DubVariant = DubVariant(
        subjectId = subjectId,
        lanName = lanName,
        lanCode = lanCode.ifBlank { language },
        original = original,
        type = type,
        detailPath = detailPath,
    )
}

/** The mobile `subject-api/get` record. */
@Serializable
data class MobileDetail(
    val subject: Subject? = null,
    val dubs: List<MobileDub> = emptyList(),
    val stars: List<Staff> = emptyList(),
    val resource: Resource? = null,
    val isForbid: Boolean = false,
) {
    /** Folds the top-level dub list into the subject, which is what the UI reads. */
    fun toTitleDetail(): TitleDetail? {
        val base = subject?.let {
            it.copy(
                detailPath = it.detailPath.ifBlank {
                    it.detailUrl.trimEnd('/').substringAfterLast('/')
                },
            )
        } ?: return null
        val mergedDubs = buildList {
            addAll(base.dubs)
            addAll(dubs.map { it.toDubVariant() })
        }.distinctBy { it.subjectId.ifBlank { it.detailPath } }

        return TitleDetail(
            subject = base.copy(dubs = mergedDubs),
            stars = stars,
            resource = resource,
            isForbid = isForbid,
        )
    }
}

/**
 * One season as the mobile `season-info` endpoint reports it.
 *
 * Here `resolutions` is a list of quality labels (`"480"`, `"720"`, `"1080"`), not the
 * `{resolution, epNum}` objects the H5 detail record carries. It is kept as JSON so
 * both forms can be normalised.
 */
@Serializable
data class MobileSeason(
    val se: Int = 0,
    val maxEp: Int = 0,
    val allEp: String = "",
    val resolutions: List<JsonElement> = emptyList(),
)

@Serializable
data class MobileSeasonInfo(
    val seasons: List<MobileSeason> = emptyList(),
)

@Serializable
data class MobileStaffPage(
    val stars: List<Staff> = emptyList(),
    val items: List<Staff> = emptyList(),
)

@Serializable
data class MobileDubInfo(
    val dubs: List<MobileDub> = emptyList(),
    val items: List<MobileDub> = emptyList(),
)

@Serializable
data class MobileRecommendPage(
    val items: List<JsonElement> = emptyList(),
)

@Serializable
data class MobileRankingPage(
    val items: List<JsonElement> = emptyList(),
    val subjectList: List<JsonElement> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class MobileComment(
    val id: String = "",
    val content: String = "",
    val userName: String = "",
    val createTime: String = "",
    val score: Int = 0,
)

@Serializable
data class MobileCommentPage(
    val items: List<MobileComment> = emptyList(),
    val list: List<MobileComment> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class MobileCommentRequest(
    val subjectId: String,
    val page: Int = 1,
    val perPage: Int = 20,
)