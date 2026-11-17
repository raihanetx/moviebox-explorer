package com.example.movieboxexplorer.data

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

suspend fun MobileApi.search(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): MobileSearchPage {
        val body = jsonFormat.encodeToString(
            MobileSearchRequest.serializer(),
            MobileSearchRequest(keyword = keyword, page = page, perPage = perPage),
        )
        for (base in bases) {
            val text = postJson(base, "$MobileConstants.MOBILE_PREFIX/subject-api/search", body) ?: continue
            val envelope = decodeEnvelope(text, MobileSearchPage.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data ?: MobileSearchPage()
        }
        return MobileSearchPage()
    }

    /** [search] with the `subject` wrapper (or a bare subject) unwrapped. */
suspend fun MobileApi.searchSubjects(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<SubjectBrief> = parseSubjects(search(keyword, page, perPage).items)

suspend fun MobileApi.searchPage(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
    ): SearchPage {
        val result = search(keyword, page, perPage)
        return SearchPage(pager = result.pager, items = parseSubjects(result.items))
    }

suspend fun MobileApi.detail(subjectId: String): MobileDetail? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/subject-api/get",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, JsonElement.serializer()) ?: continue
            if (envelope.code != 0) continue
            val data = envelope.data as? JsonObject ?: continue

            // Some builds wrap the record in `data.subject`; others return the
            // subject fields directly under `data`.
            val wrapped = data["subject"] as? JsonObject
            val detail = if (wrapped != null) {
                runCatching {
                    jsonFormat.decodeFromJsonElement(MobileDetail.serializer(), data)
                }.getOrNull()?.takeIf { it.subject?.subjectId?.isNotBlank() == true }
                    ?.let { mobileDetail ->
                        val subject = mobileDetail.subject!!
                        mobileDetail.copy(
                            subject = subject.copy(
                                detailPath = subject.detailPath.ifBlank {
                                    subject.detailUrl.trimEnd('/').substringAfterLast('/')
                                },
                            ),
                        )
                    }
            } else {
                val subject = runCatching {
                    jsonFormat.decodeFromJsonElement(Subject.serializer(), data)
                }.getOrNull()
                subject
                    ?.takeIf { it.subjectId.isNotBlank() }
                    ?.let {
                        MobileDetail(
                            subject = it.copy(
                                detailPath = it.detailPath.ifBlank {
                                    it.detailUrl.trimEnd('/').substringAfterLast('/')
                                },
                            ),
                        )
                    }
            }
            if (detail?.subject != null) return detail
        }
        return null
    }

suspend fun MobileApi.seasonInfo(subjectId: String): Resource? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/subject-api/season-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileSeasonInfo.serializer()) ?: continue
            if (envelope.code == 0) {
                val resource = envelope.data?.toResource()
                if (resource != null && resource.seasons.isNotEmpty()) return resource
            }
        }
        return null
    }

suspend fun MobileApi.dubInfo(subjectId: String): List<MobileDub> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/subject-api/dub-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileDubInfo.serializer()) ?: continue
            if (envelope.code == 0) {
                val dubs = envelope.data?.dubs?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.items
                    ?: emptyList()
                if (dubs.isNotEmpty()) return dubs
            }
        }
        return emptyList()
    }

suspend fun MobileApi.staffInfo(subjectId: String): List<Staff> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/subject-api/staff-info",
                mapOf("subjectId" to subjectId),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileStaffPage.serializer()) ?: continue
            if (envelope.code == 0) {
                val staff = envelope.data?.stars?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.items
                    ?: emptyList()
                if (staff.isNotEmpty()) return staff
            }
        }
        return emptyList()
    }

suspend fun MobileApi.recommend(
        subjectId: String,
        page: Int = 1,
        perPage: Int = 12,
    ): List<SubjectBrief> {
        if (subjectId.isBlank()) return emptyList()
        for (base in bases) {
            for (path in MobileConstants.RECOMMEND_PATHS) {
                val text = getJson(
                    base,
                    path,
                    mapOf(
                        "subjectId" to subjectId,
                        "page" to page.toString(),
                        "perPage" to perPage.toString(),
                    ),
                ) ?: continue
                val envelope = decodeEnvelope(text, MobileRecommendPage.serializer()) ?: continue
                if (envelope.code == 0) {
                    val subjects = parseSubjects(envelope.data?.items.orEmpty())
                    if (subjects.isNotEmpty()) return subjects
                }
            }
        }
        return emptyList()
    }

suspend fun MobileApi.rankingList(
        categoryId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): MobileRankingPage {
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/tab/ranking-list",
                mapOf(
                    "tabId" to "0",
                    "categoryType" to categoryId,
                    "page" to page.toString(),
                    "perPage" to perPage.toString(),
                ),
            ) ?: continue
            val envelope = decodeEnvelope(text, MobileRankingPage.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data ?: MobileRankingPage()
        }
        return MobileRankingPage()
    }

suspend fun MobileApi.rankingSubjects(
        categoryId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<SubjectBrief> {
        val page = rankingList(categoryId, page, perPage)
        return parseSubjects(page.items.ifEmpty { page.subjectList })
    }

suspend fun MobileApi.comments(
        subjectId: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<MobileComment> {
        if (subjectId.isBlank()) return emptyList()
        val body = jsonFormat.encodeToString(
            MobileCommentRequest.serializer(),
            MobileCommentRequest(subjectId, page, perPage),
        )
        for (base in bases) {
            val text = postJson(base, "$MobileConstants.MOBILE_PREFIX/subject-api/comment/list", body) ?: continue
            val envelope = decodeEnvelope(text, MobileCommentPage.serializer()) ?: continue
            if (envelope.code == 0) {
                val comments = envelope.data?.items?.takeIf { it.isNotEmpty() }
                    ?: envelope.data?.list
                    ?: emptyList()
                if (comments.isNotEmpty()) return comments
            }
        }
        return emptyList()
    }

    /**
     * The mobile play endpoint. The guide reports that it only returns promo
     * renditions, so this is exposed for diagnostics rather than used for playback.
     */
suspend fun MobileApi.playInfo(
        subjectId: String,
        season: Int = 1,
        episode: Int = 1,
        isVip: Boolean = false,
    ): PlayData? {
        if (subjectId.isBlank()) return null
        for (base in bases) {
            val text = getJson(
                base,
                "$MobileConstants.MOBILE_PREFIX/subject-api/play-info",
                mapOf(
                    "subjectId" to subjectId,
                    "se" to season.toString(),
                    "ep" to episode.toString(),
                    "isVip" to isVip.toString(),
                ),
            ) ?: continue
            val envelope = decodeEnvelope(text, PlayData.serializer()) ?: continue
            if (envelope.code == 0) return envelope.data
        }
        return null
    }


private fun MobileSeasonInfo.toResource(): Resource {
    val seasons = seasons.map { season ->
        val labels = season.resolutions.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.toIntOrNull()
                is JsonObject -> (element["resolution"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.toIntOrNull()
                else -> null
            }
        }
        val resolutions = if (labels.isNotEmpty()) {
            labels.map { Resolution(resolution = it, epNum = 0) }
        } else {
            season.resolutions.mapNotNull { element ->
                (element as? JsonObject)?.let { objectElement ->
                    val resolution = (objectElement["resolution"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.toIntOrNull()
                        ?: return@mapNotNull null
                    val epNum = (objectElement["epNum"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.toIntOrNull()
                        ?: 0
                    Resolution(resolution, epNum)
                }
            }
        }
        val maxEp = season.maxEp.takeIf { it > 0 }
            ?: season.allEp.split(",").count { it.isNotBlank() }
        Season(
            se = season.se,
            maxEp = maxEp,
            allEp = season.allEp,
            resolutions = resolutions,
        )
    }
    return Resource(seasons = seasons)
}