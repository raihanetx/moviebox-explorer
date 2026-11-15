package com.example.movieboxexplorer.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The mobile API reports several numeric fields as `""` when the value is unknown.
 * This serializer reads an int from either a JSON number or a numeric string, and
 * falls back to `0` for anything else.
 */
object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.let { primitive ->
            primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt()
        } ?: 0
    }
}

/** Every API response is wrapped in `{code, message, reason, data}`. */
@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val message: String = "",
    val reason: String = "",
    val data: T? = null,
)

@Serializable
data class Cover(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class Pager(
    val page: String = "",
    val nextPage: String = "",
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
)

@Serializable
data class SubjectBrief(
    val subjectId: String = "",
    @Serializable(with = LenientIntSerializer::class) val subjectType: Int = 1,
    val title: String = "",
    val description: String = "",
    val releaseDate: String = "",
    @Serializable(with = LenientIntSerializer::class) val duration: Int = 0,
    val genre: String = "",
    val countryName: String = "",
    val imdbRatingValue: String = "",
    @Serializable(with = LenientIntSerializer::class) val imdbRatingCount: Int = 0,
    val subtitles: String = "",
    val hasResource: Boolean = false,
    val detailPath: String = "",
    val cover: Cover? = null,
    val trailer: Trailer? = null,
    val dubs: List<DubVariant> = emptyList(),
    @Serializable(with = LenientIntSerializer::class) val season: Int = 0,
    val ops: String = "",
    @Serializable(with = LenientIntSerializer::class) val appointmentCnt: Int = 0,
    val appointmentDate: String = "",
    val corner: String = "",
    val stills: JsonElement? = null,
    val postTitle: String = "",
    val accessStrategy: JsonElement? = null,
    val webHighRisk: Boolean = false,
    /**
     * Adult-content flag. The listing endpoints always report `false` here, so this
     * field is only ever meaningful on a [Subject] — the detail record. See
     * [RiskFlags].
     */
    val isPornographic: Boolean = false,
)

/**
 * One subtitle track of an episode.
 *
 * The endpoint returns an empty list for titles that carry no separate subtitle files,
 * so every field is optional.
 */
@Serializable
data class Caption(
    val id: String = "",
    val lan: String = "",
    val lanName: String = "",
    val url: String = "",
    val type: Int = 0,
)

/** The body of `GET /subject/caption`. */
@Serializable
data class CaptionPage(
    val captions: List<Caption> = emptyList(),
)

@Serializable
data class SearchCount(
    val subjectType: Int = 0,
    val name: String = "",
    val num: Int = 0,
)

@Serializable
data class SearchPage(
    val pager: Pager? = null,
    val items: List<SubjectBrief> = emptyList(),
    val counts: List<SearchCount> = emptyList(),
)

/** One platform (Netflix, PrimeVideo, …) surfaced on the home page. */
@Serializable
data class Platform(
    val name: String = "",
    val uploadBy: String = "",
)

/** A single slide in a home-page banner carousel. */
@Serializable
data class BannerItem(
    val id: String = "",
    val title: String = "",
    val image: Cover? = null,
    val url: String = "",
    val subjectId: String = "",
    val subjectType: Int = 0,
    val subject: SubjectBrief? = null,
)

@Serializable
data class Banner(
    val items: List<BannerItem> = emptyList(),
)

/**
 * One row on a catalog page. The API mixes several row types:
 * `BANNER`, `SPORT_LIVE`, `SUBJECTS_MOVIE`, `FILTER`, `APPOINTMENT_LIST`.
 */
@Serializable
data class OperatingSection(
    val type: String = "",
    val position: Int = 0,
    val title: String = "",
    val subjects: List<SubjectBrief> = emptyList(),
    val banner: Banner? = null,
    val genreId: String = "",
    val rankingGenreId: String = "",
    val rankingTitle: String = "",
    val titleType: String = "",
    val rankingSubjectList: List<SubjectBrief> = emptyList(),
    /** The website drops the row with this id from the home page. */
    val opId: String = "",
)

/** One selectable value of a filter group (`Drama`, `Japan`, `2026`, `Hottest`). */
@Serializable
data class FilterValue(
    val id: String = "",
    val name: String = "",
)

/** The numeric range of a filter group (the rating filter). */
@Serializable
data class RangeVals(
    val minVal: String = "",
    val maxVal: String = "",
)

/** One filter group of a catalog page. */
@Serializable
data class FilterGroup(
    val filterType: String = "",
    val title: String = "",
    val filterVals: List<FilterValue> = emptyList(),
    val rangeVals: RangeVals? = null,
    val showOut: Boolean = false,
)

/**
 * The filter configuration of one catalog page.
 *
 * `channelId` is the channel the page lists — `2` for Series, `1006` for Anime — and
 * it is what the "load more" request is addressed at.
 */
@Serializable
data class FilterConfig(
    val channelId: Int = 0,
    val channelName: String = "",
    val items: List<FilterGroup> = emptyList(),
)

/** The filter configuration of a page, as the payload carries it. */
@Serializable
data class FilterItemsData(
    val typeList: List<FilterConfig> = emptyList(),
    val mobile: List<FilterConfig> = emptyList(),
)

@Serializable
data class HomePage(
    val platformList: List<Platform> = emptyList(),
    val operatingList: List<OperatingSection> = emptyList(),
)

@Serializable
data class TabOperatingPage(
    val operatingList: List<OperatingSection> = emptyList(),
)

@Serializable
data class FilterPage(
    val pager: Pager? = null,
    val items: List<SubjectBrief> = emptyList(),
)

@Serializable
data class FilterRequest(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val page: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val perPage: Int = 18,
    val channelId: Int = 1,
    /** Omitted from the body when unset — an empty string makes the API reject it. */
    val genre: String? = null,
    val country: String? = null,
    val year: String? = null,
    val sort: String? = null,
    val rate: String? = null,
    val classify: String? = null,
)

@Serializable
data class TrendingPage(
    val subjectList: List<SubjectBrief> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class EveryoneSearchTerm(
    val title: String = "",
)

@Serializable
data class HotGroup(
    val category: String = "",
    val name: String = "",
    val items: List<SubjectBrief> = emptyList(),
)

@Serializable
data class EveryoneSearchPage(
    val everyoneSearch: List<EveryoneSearchTerm> = emptyList(),
    val hot: List<HotGroup> = emptyList(),
)

@Serializable
data class RankingPage(
    val title: String = "",
    val subjectList: List<SubjectBrief> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class DetailRecPage(
    val items: List<SubjectBrief> = emptyList(),
)

@Serializable
data class RankingCategory(
    val name: String = "",
    val id: String = "",
    val path: String = "",
)

@Serializable
data class MostWatchedPage(
    val rankingList: List<RankingCategory> = emptyList(),
    val subjectList: List<SubjectBrief> = emptyList(),
)

/**
 * One language variant of a title. The site stores every dub/subtitle track as its
 * *own* subject, so [subjectId] and [detailPath] point at a completely separate record.
 *
 * @param type 0 = dub, 1 = subtitle
 */
@Serializable
data class DubVariant(
    val subjectId: String = "",
    val lanName: String = "",
    val lanCode: String = "",
    val original: Boolean = false,
    val type: Int = 0,
    val detailPath: String = "",
)

@Serializable
data class Resolution(
    val resolution: Int = 0,
    val epNum: Int = 0,
)

@Serializable
data class Season(
    val se: Int = 0,
    val maxEp: Int = 0,
    val allEp: String = "",
    val resolutions: List<Resolution> = emptyList(),
)

@Serializable
data class Resource(
    val seasons: List<Season> = emptyList(),
    val source: String = "",
    val uploadBy: String = "",
)

@Serializable
data class Staff(
    val staffId: String = "",
    val staffType: Int = 0,
    val name: String = "",
    val character: String = "",
    val avatarUrl: String = "",
    val detailPath: String = "",
)

@Serializable
data class TrailerVideo(
    val url: String = "",
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class Trailer(
    val videoAddress: TrailerVideo? = null,
    val cover: Cover? = null,
)

@Serializable
data class Subject(
    val subjectId: String = "",
    @Serializable(with = LenientIntSerializer::class) val subjectType: Int = 1,
    val title: String = "",
    val description: String = "",
    val releaseDate: String = "",
    @Serializable(with = LenientIntSerializer::class) val duration: Int = 0,
    val genre: String = "",
    val countryName: String = "",
    val imdbRatingValue: String = "",
    @Serializable(with = LenientIntSerializer::class) val imdbRatingCount: Int = 0,
    val subtitles: String = "",
    val hasResource: Boolean = false,
    val detailPath: String = "",
    /** Some mobile records call it `detailUrl` instead of `detailPath`. */
    val detailUrl: String = "",
    val cover: Cover? = null,
    val trailer: Trailer? = null,
    val dubs: List<DubVariant> = emptyList(),
    @Serializable(with = LenientIntSerializer::class) val season: Int = 0,
    val webHighRisk: Boolean = false,
    /**
     * Adult-content flag. **Only the detail record carries a meaningful value** — the
     * listing endpoints always report `false`, so this can only be trusted once a
     * title has been opened. See [RiskFlags].
     */
    val isPornographic: Boolean = false,
)

@Serializable
data class TitleDetail(
    val subject: Subject? = null,
    val stars: List<Staff> = emptyList(),
    val resource: Resource? = null,
    val isForbid: Boolean = false,
    val watchTimeLimit: Int = 0,
)

@Serializable
data class SuggestWord(
    val type: Int = 0,
    val word: String = "",
)

@Serializable
data class SuggestPage(
    val items: List<SuggestWord> = emptyList(),
    val keyword: String = "",
)

@Serializable
data class SearchRequest(
    val keyword: String,
    val page: Int = 1,
    val perPage: Int = 10,
    val subjectType: Int = 2,
)

@Serializable
data class SuggestRequest(
    val keyword: String,
    val perPage: Int = 10,
)
