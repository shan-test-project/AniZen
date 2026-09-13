# Fix spec: MAL (Jikan) fallback for Prequel/Sequel + Character cards

Repo: `shan-test-project/AniZen`
Branch: `master`

Goal: when AniList can't produce relations or character cards for an anime,
fall back to MyAnimeList data via the free public Jikan API
(`https://api.jikan.moe/v4`), **without requiring any tracker login** and
**without disturbing the existing AniList-only flow** when AniList succeeds.

## Critical constraint: AniList and MAL do not share IDs

AniList media IDs and MyAnimeList IDs are two independent numbering schemes.
Never reuse an AniList id as a MAL id or vice versa. Every function below
that talks to Jikan must resolve its own MAL id — either through
`arm.haglund.dev` (id-mapping service) or through a fresh Jikan title search.
Do not attempt to derive one id from the other by any kind of arithmetic or
string reuse.

## New file: `app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/JikanApi.kt`

Standalone client. Do **not** reuse the existing authenticated `MyAnimeList`
tracker class or its OkHttp client — this must work for users who have never
logged into any tracker. Use `networkHelper.client` (the plain, unauthenticated
client), the same way `AnilistApi`'s public queries already do.

```kotlin
package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.data.track.anilist.ALRelationEdge // reuse the existing model — see note below
import eu.kanade.tachiyomi.animesource.model.Credit
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class JikanApi(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    /**
     * Exact AniList-id -> MAL-id lookup via the community id-mapping service.
     * Returns null on any failure (service down, no mapping found, etc.) —
     * callers must fall back to [searchMalIdByTitle], never guess.
     */
    suspend fun malIdFromAniListId(aniListId: Int): Int? = withIOContext {
        try {
            val request = Request.Builder()
                .url("https://arm.haglund.dev/api/v2/ids?source=anilist&id=$aniListId&include=myanimelist")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withIOContext null
                val body = response.body.string()
                json.decodeFromString(ArmMappingResponse.serializer(), body).myanimelist
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Fuzzy fallback when there's no AniList id at all, or the mapping service failed. */
    suspend fun searchMalIdByTitle(title: String): Int? = withIOContext {
        try {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime?q=${java.net.URLEncoder.encode(title, "UTF-8")}&limit=1")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withIOContext null
                val body = response.body.string()
                json.decodeFromString(JikanSearchResponse.serializer(), body)
                    .data.firstOrNull()?.malId
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRelations(malId: Int): List<ALRelationEdge> = withIOContext {
        try {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId/relations")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withIOContext emptyList()
                val body = response.body.string()
                json.decodeFromString(JikanRelationsResponse.serializer(), body)
                    .data
                    .filter { it.relation.equals("Prequel", true) || it.relation.equals("Sequel", true) }
                    .flatMap { group ->
                        val relationType = if (group.relation.equals("Prequel", true)) "PREQUEL" else "SEQUEL"
                        group.entry.map { entry ->
                            // Map into the SAME shape PrequelSequelBox/ALRelationEdge already
                            // consumes. malId is stored where the AniList numeric id would go —
                            // this is fine because the tap handler resolves by TITLE against the
                            // current extension (see AnimeScreenModel.openRelatedAnimeInSource),
                            // not by numeric id, so a MAL id in that slot is safe.
                            ALRelationEdge(relationType = relationType, node = /* map entry.malId, entry.name, entry.images */ TODO())
                        }
                    }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCharacters(malId: Int): List<Credit> = withIOContext {
        try {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId/characters")
                .build()
            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withIOContext emptyList()
                val body = response.body.string()
                json.decodeFromString(JikanCharactersResponse.serializer(), body)
                    .data.map { entry ->
                        val mainVa = entry.voiceActors.firstOrNull { it.language.equals("Japanese", true) }
                            ?: entry.voiceActors.firstOrNull()
                        Credit(
                            name = mainVa?.person?.name ?: entry.character.name,
                            image_url = mainVa?.person?.images?.jpg?.imageUrl
                                ?: entry.character.images.jpg.imageUrl,
                            role = entry.role,
                            character = entry.character.name,
                        )
                    }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class ArmMappingResponse(val myanimelist: Int? = null)

    @Serializable
    private data class JikanSearchResponse(val data: List<JikanSearchEntry> = emptyList())
    @Serializable
    private data class JikanSearchEntry(val malId: Int)
    // NOTE: real Jikan field is "mal_id" — add @kotlinx.serialization.SerialName("mal_id")
    // on every malId property below. Omitted here for brevity; the agent implementing this
    // MUST add the SerialName annotations or deserialization will silently fail with 0 results.

    @Serializable
    private data class JikanRelationsResponse(val data: List<JikanRelationGroup> = emptyList())
    @Serializable
    private data class JikanRelationGroup(val relation: String, val entry: List<JikanRelationEntry> = emptyList())
    @Serializable
    private data class JikanRelationEntry(val malId: Int, val name: String)

    @Serializable
    private data class JikanCharactersResponse(val data: List<JikanCharacterEntry> = emptyList())
    @Serializable
    private data class JikanCharacterEntry(
        val character: JikanCharacterInfo,
        val role: String,
        val voiceActors: List<JikanVoiceActor> = emptyList(),
    )
    @Serializable
    private data class JikanCharacterInfo(val name: String, val images: JikanImageWrapper)
    @Serializable
    private data class JikanVoiceActor(val person: JikanPerson, val language: String)
    @Serializable
    private data class JikanPerson(val name: String, val images: JikanImageWrapper)
    @Serializable
    private data class JikanImageWrapper(val jpg: JikanImage)
    @Serializable
    private data class JikanImage(val imageUrl: String)
}
```

**Important note on `ALRelationEdge`**: check its actual constructor shape in
`app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/AnilistApi.kt`
before writing the mapper above — the `TODO()` placeholder needs real field
names (likely `node.id`, `node.title`, `node.coverImage`). Match it exactly so
`PrequelSequelBox.kt` needs zero changes.

**Rate limiting**: Jikan's public tier is ~3 requests/second, 60/minute. This
integration makes at most 2–3 calls per anime screen open (id resolution +
relations + characters), only when AniList already failed, so normal usage
won't come close to the limit. Do not add a client-side rate limiter for v1 —
just let 429s fail through the existing try/catch (returns empty, which is
the correct no-op).

## Hook 1: Prequel/Sequel relations fallback

Location: `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt`,
inside `requestRelations()`.

**Do not change the existing priority chain** (tracked AniList →
extension-id → AniList title search) or its dedup/`relationRequestKey`
logic. Add the Jikan attempt as a pure extra step *after* the existing
AniList-based `request()` call resolves, only if it came back empty:

```kotlin
private fun requestRelations(key: String, request: suspend () -> List<ALRelationEdge>) {
    if (relationRequestKey == key) return
    relationRequestKey = key
    updateSuccessState { it.copySuccess(hasFetchedRelations = true) }
    screenModelScope.launchIO {
        try {
            var relations = request()
            if (relations.isEmpty()) {
                relations = fetchRelationsFromJikanFallback()
            }
            if (relationRequestKey == key) {
                updateSuccessState { it.copySuccess(relations = relations.toImmutableList()) }
            }
        } catch (e: Exception) {
            if (relationRequestKey == key) {
                relationRequestKey = null
                updateSuccessState { it.copySuccess(relations = emptyList(), hasFetchedRelations = false) }
            }
        }
    }
}

private suspend fun fetchRelationsFromJikanFallback(): List<ALRelationEdge> {
    val state = successState ?: return emptyList()
    val jikanApi = JikanApi() // or inject via Injekt.get() if registered as a singleton
    val storedAniListId = state.trackItems
        .firstOrNull { it.tracker is eu.kanade.tachiyomi.data.track.anilist.Anilist && it.track != null }
        ?.track?.remoteId?.toInt()
    val malId = storedAniListId?.let { jikanApi.malIdFromAniListId(it) }
        ?: jikanApi.searchMalIdByTitle(canonicalAnimeTitle(state.anime))
        ?: return emptyList()
    return jikanApi.getRelations(malId)
}
```

`canonicalAnimeTitle(...)` — reuse whatever helper `observeTrackers()`
already uses to build `canonicalAnimeTitle` for the existing AniList
title-search branch; don't write a second title-normalization function.

This only ever runs when the AniList-based `request()` returned an empty
list (AniList reachable but genuinely had no relations, or the earlier
title-search/tracked lookup failed) — it never runs instead of AniList, only
after it, and only on empty, never on a thrown exception path (that path is
already handled by the existing catch block and should stay AniList-only per
the earlier caching fix).

## Hook 2: Character cards fallback

`state.anime.cast` (`List<Credit>?`) is a field on the persisted domain
`Anime` model, not a live per-screen-open ScreenModel field like `relations`
— it's populated wherever the app currently fetches AniList character data
during anime metadata refresh, then saved to the database.

**Before writing any code**: locate that existing function. Search for where
`Credit(` objects are constructed from AniList character data, or where
`.cast` is assigned on an `Anime`/`AnimeUpdate` object — most likely in
`Anilist.kt`, or in whatever interactor performs the anime info refresh
(look near `SetAnimeCastInteractor`, `UpdateAnime`, or similar naming in
`domain/anime/interactor`). Once found:

- If that function returns an empty list from the AniList query, call
  `jikanApi.getCharacters(malId)` as a fallback the same way as Hook 1 (same
  id-resolution order: verified AniList↔MAL mapping first, title search
  second).
- The result is already shaped as `List<Credit>` by the mapper above, so it
  can be assigned to the same `cast` field with no changes to `CastRow.kt`
  or `CreditDetailsDialog.kt`.
- Respect whatever caching/persistence the existing function already does —
  if it saves the AniList result to the DB once fetched, the Jikan result
  should be saved through the exact same path, not a parallel one.

## Acceptance criteria

1. For an anime where AniList already returns relations/characters, output
   is byte-identical to before this change — Jikan is never called.
2. For an anime where AniList's relations list comes back empty, Jikan is
   tried, using the tracked-AniList-id → MAL-id mapping first, then a title
   search, and only shows a Prequel/Sequel card if Jikan found one.
3. Same for characters: only falls back on an empty AniList characters list.
4. No user login/tracker binding is required for the fallback to work —
   verify by testing on a completely fresh install with zero trackers logged
   in.
5. AniList and MAL ids are never cross-used; every Jikan call resolves its
   own id through `malIdFromAniListId` or `searchMalIdByTitle`.
6. If Jikan is also down/empty, both features simply show nothing — same
   as today's behavior when AniList has nothing. No crash, no infinite
   retry.
