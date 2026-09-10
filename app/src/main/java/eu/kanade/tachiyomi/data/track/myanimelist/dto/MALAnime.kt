package eu.kanade.tachiyomi.data.track.myanimelist.dto

import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationCoverImage
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationEdge
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationTitle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALAnime(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_episodes")
    val numEpisodes: Long,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALAnimeCovers,
    val status: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("start_date")
    val startDate: String?,
)

@Serializable
data class MALAnimeCovers(
    val large: String = "",
    val medium: String,
)

@Serializable
data class MALAnimeRelations(
    @SerialName("related_anime")
    val relatedAnime: List<MALRelatedAnime> = emptyList(),
)

@Serializable
data class MALRelatedAnime(
    val node: MALRelatedAnimeNode,
    @SerialName("relation_type")
    val relationType: String,
)

@Serializable
data class MALRelatedAnimeNode(
    val id: Long,
    val title: String,
    @SerialName("main_picture")
    val covers: MALAnimeCovers? = null,
)

fun MALAnimeRelations.toRelationEdges(): List<ALRelationEdge> {
    return relatedAnime.mapNotNull { related ->
        val relationType = when (related.relationType.lowercase()) {
            "prequel" -> "PREQUEL"
            "sequel" -> "SEQUEL"
            else -> return@mapNotNull null
        }
        ALRelationEdge(
            relationType = relationType,
            node = ALRelationNode(
                id = related.node.id.toInt(),
                title = ALRelationTitle(
                    userPreferred = related.node.title,
                    romaji = related.node.title,
                    english = related.node.title,
                    native = related.node.title,
                ),
                coverImage = related.node.covers?.let {
                    ALRelationCoverImage(it.large.ifBlank { it.medium })
                },
            ),
        )
    }
}
