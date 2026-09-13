package eu.kanade.tachiyomi.data.track.myanimelist

import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.Credit
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationCoverImage
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationEdge
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationTitle
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Anonymous Jikan client used only as a metadata fallback.
 *
 * This deliberately does not reuse [MyAnimeListApi], which requires a logged-in
 * MAL tracker. AniList and MAL IDs are resolved independently before any Jikan
 * endpoint is called.
 */
class JikanApi(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    suspend fun resolveMalId(aniListId: Long?, titles: List<String>): Int? {
        if (aniListId != null && aniListId > 0L) {
            malIdFromAniListId(aniListId.toInt())?.let { return it }
        }

        for (title in titles.map(String::trim).filter(String::isNotBlank).distinct()) {
            searchMalIdByTitle(title)?.let { return it }
        }
        return null
    }

    private suspend fun malIdFromAniListId(aniListId: Int): Int? = withIOContext {
        runCatching {
            val request = Request.Builder()
                .url(
                    "https://arm.haglund.dev/api/v2/ids" +
                        "?source=anilist&id=$aniListId&include=myanimelist",
                )
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<ArmMappingResponse>(response.body.string()).myanimelist
            }
        }.getOrNull()
    }

    private suspend fun searchMalIdByTitle(title: String): Int? = withIOContext {
        runCatching {
            val url = Uri.parse("https://api.jikan.moe/v4/anime").buildUpon()
                .appendQueryParameter("q", title)
                .appendQueryParameter("limit", "1")
                .build()
            val request = Request.Builder().url(url.toString()).build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<JikanSearchResponse>(response.body.string())
                    .data
                    .firstOrNull()
                    ?.malId
            }
        }.getOrNull()
    }

    suspend fun getRelations(malId: Int): List<ALRelationEdge> = withIOContext {
        runCatching {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId/relations")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<JikanRelationsResponse>(response.body.string())
                    .data
                    .filter {
                        it.relation.equals("Prequel", ignoreCase = true) ||
                            it.relation.equals("Sequel", ignoreCase = true)
                    }
                    .flatMap { group ->
                        val relationType = if (group.relation.equals("Prequel", true)) {
                            "PREQUEL"
                        } else {
                            "SEQUEL"
                        }
                        group.entry.map { entry ->
                            ALRelationEdge(
                                relationType = relationType,
                                node = ALRelationNode(
                                    // This is a MAL ID. The relation card opens by title,
                                    // so it is never used as an AniList ID.
                                    id = entry.malId,
                                    title = ALRelationTitle(
                                        userPreferred = entry.name,
                                        romaji = entry.name,
                                        english = entry.name,
                                        native = entry.name,
                                    ),
                                    coverImage = entry.images?.jpg?.imageUrl?.let {
                                        ALRelationCoverImage(it)
                                    },
                                ),
                            )
                        }
                    }
            }
        }.getOrElse { emptyList() }
    }

    suspend fun getCharacters(malId: Int): List<Credit> = withIOContext {
        runCatching {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId/characters")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<JikanCharactersResponse>(response.body.string())
                    .data
                    .map { entry ->
                        val voiceActor = entry.voiceActors.firstOrNull {
                            it.language.equals("Japanese", ignoreCase = true)
                        } ?: entry.voiceActors.firstOrNull()
                        val character = entry.character
                        Credit(
                            name = character.name,
                            role = voiceActor?.person?.name ?: entry.role,
                            character = character.name,
                            image_url = character.images?.jpg?.imageUrl
                                ?: voiceActor?.person?.images?.jpg?.imageUrl,
                            url = "https://myanimelist.net/character/${character.malId}",
                        )
                    }
            }
        }.getOrElse { emptyList() }
    }

    @Serializable
    private data class ArmMappingResponse(
        val myanimelist: Int? = null,
    )

    @Serializable
    private data class JikanSearchResponse(
        val data: List<JikanSearchEntry> = emptyList(),
    )

    @Serializable
    private data class JikanSearchEntry(
        @SerialName("mal_id") val malId: Int,
    )

    @Serializable
    private data class JikanRelationsResponse(
        val data: List<JikanRelationGroup> = emptyList(),
    )

    @Serializable
    private data class JikanRelationGroup(
        val relation: String,
        val entry: List<JikanRelationEntry> = emptyList(),
    )

    @Serializable
    private data class JikanRelationEntry(
        @SerialName("mal_id") val malId: Int,
        val name: String,
        val images: JikanImages? = null,
    )

    @Serializable
    private data class JikanCharactersResponse(
        val data: List<JikanCharacterEntry> = emptyList(),
    )

    @Serializable
    private data class JikanCharacterEntry(
        val character: JikanCharacterInfo,
        val role: String,
        @SerialName("voice_actors") val voiceActors: List<JikanVoiceActor> = emptyList(),
    )

    @Serializable
    private data class JikanCharacterInfo(
        @SerialName("mal_id") val malId: Int,
        val name: String,
        val images: JikanImages? = null,
    )

    @Serializable
    private data class JikanVoiceActor(
        val person: JikanPerson,
        val language: String,
    )

    @Serializable
    private data class JikanPerson(
        val name: String,
        val images: JikanImages? = null,
    )

    @Serializable
    private data class JikanImages(
        val jpg: JikanImage? = null,
    )

    @Serializable
    private data class JikanImage(
        @SerialName("image_url") val imageUrl: String? = null,
    )
}