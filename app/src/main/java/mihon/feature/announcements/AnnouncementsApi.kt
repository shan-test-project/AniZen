package mihon.feature.announcements

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneOffset

class AnnouncementsApi(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchAnnouncements(maxPages: Int = 4): List<AnnouncementEntry> = withIOContext {
        val seenIds = mutableSetOf<Int>()
        val results = mutableListOf<AnnouncementEntry>()

        for (page in 1..maxPages) {
            val mediaList = fetchPage(page)
            if (mediaList.isEmpty()) break

            mediaList.forEach { dto ->
                if (seenIds.add(dto.id)) {
                    dto.toAnnouncementEntry()?.let { results.add(it) }
                }
            }

            if (mediaList.size < PER_PAGE) break
        }

        results
    }

    private suspend fun fetchPage(page: Int): List<MediaDto> {
        val requestBody = GraphQlRequest(
            query = QUERY,
            variables = mapOf("page" to page, "perPage" to PER_PAGE),
        )
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(json.encodeToString(requestBody).toRequestBody(jsonMediaType))
            .build()

        return networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("AniList announcements query failed: HTTP ${response.code}")
            }
            val body = response.body.string()
            json.decodeFromString<GraphQlResponse>(body).data?.Page?.media ?: emptyList()
        }
    }

    private fun MediaDto.toAnnouncementEntry(): AnnouncementEntry? {
        val titleText = title.userPreferred ?: return null
        val prequelEdge = relations?.edges?.firstOrNull { edge ->
            edge.relationType == "PREQUEL" && edge.node.type == "ANIME"
        }
        val relatedTitle = prequelEdge?.node?.title?.userPreferred
        val category = AnnouncementTextBuilder.categoryFor(
            format = format,
            source = source,
            hasPrequelEdge = prequelEdge != null,
            currentTitle = titleText,
            relatedTitle = relatedTitle,
        ) ?: return null

        val relatedYear = prequelEdge?.node?.startDate?.year
        val expectedYear = startDate?.year
        val exactReleaseDate = startDate
            ?.takeIf { it.year != null && it.month != null && it.day != null }
            ?.let { sd ->
                LocalDate.of(sd.year!!, sd.month!!, sd.day!!)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }

        return AnnouncementEntry(
            mediaId = id,
            title = titleText,
            category = category,
            description = AnnouncementTextBuilder.buildDescription(
                title = titleText,
                category = category,
                format = format,
                source = source,
                relatedTitle = relatedTitle,
                relatedYear = relatedYear,
                expectedYear = expectedYear,
            ),
            expectedYear = expectedYear,
            relatedAniListMediaId = prequelEdge?.node?.id,
            relatedTitle = relatedTitle,
            relatedYear = relatedYear,
            coverImageUrl = coverImage?.extraLarge ?: coverImage?.medium,
            bannerImageUrl = bannerImage,
            exactReleaseDate = exactReleaseDate,
            fetchedFrom = "AniList API",
        )
    }

    companion object {
        private const val PER_PAGE = 25

        private const val QUERY = """
            query Announcements(${'$'}page: Int, ${'$'}perPage: Int) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(status: NOT_YET_RELEASED, sort: [POPULARITY_DESC], type: ANIME) {
                        id
                        title { userPreferred }
                        format
                        source
                        startDate { year month day }
                        coverImage { extraLarge medium color }
                        bannerImage
                        relations {
                            edges {
                                relationType(version: 2)
                                node {
                                    id
                                    title { userPreferred }
                                    startDate { year }
                                    type
                                }
                            }
                        }
                    }
                }
            }
        """
    }
}

@Serializable
private data class GraphQlRequest(val query: String, val variables: Map<String, Int>)

@Serializable
private data class GraphQlResponse(val data: PageDataWrapper? = null)

@Serializable
private data class PageDataWrapper(val Page: PageData)

@Serializable
private data class PageData(val media: List<MediaDto> = emptyList())

@Serializable
private data class MediaDto(
    val id: Int,
    val title: TitleDto,
    val format: String? = null,
    val source: String? = null,
    val startDate: StartDateDto? = null,
    val coverImage: CoverImageDto? = null,
    val bannerImage: String? = null,
    val relations: RelationsDto? = null,
)

@Serializable
private data class TitleDto(val userPreferred: String? = null)

@Serializable
private data class StartDateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
private data class CoverImageDto(
    val extraLarge: String? = null,
    val medium: String? = null,
    val color: String? = null,
)

@Serializable
private data class RelationsDto(val edges: List<RelationEdgeDto> = emptyList())

@Serializable
private data class RelationEdgeDto(
    val relationType: String? = null,
    val node: RelationNodeDto,
)

@Serializable
private data class RelationNodeDto(
    val id: Int,
    val title: TitleDto,
    val startDate: StartDateDto? = null,
    val type: String? = null,
)