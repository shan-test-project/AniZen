package eu.kanade.tachiyomi.data.track.anilist

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.animesource.model.Credit
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.ImportableTracker
import eu.kanade.tachiyomi.data.track.ImportableEntry
import eu.kanade.tachiyomi.data.track.ImportStatusFilter
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRelationEdge
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainAnimeTrack

class Anilist(id: Long) :
    BaseTracker(
        id,
        "AniList",
    ),
    AnimeTracker,
    DeletableTracker,
    ImportableTracker {

    companion object {
        const val READING = 1L
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val PLAN_TO_WATCH = 15L
        const val REREADING = 6L
        const val REWATCHING = 16L

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this, getPassword()) }

    val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    private val scorePreference = trackPreferences.anilistScoreType()
    private val preferenceStore: PreferenceStore by injectLazy()

    init {
        // If the preference is an int from APIv1, logout user to force using APIv2
        try {
            scorePreference.get()
        } catch (e: ClassCastException) {
            logout()
            scorePreference.delete()
        }
    }

    override fun getLogo() = R.drawable.ic_tracker_anilist

    override fun getLogoColor() = Color.rgb(18, 25, 35)

    override fun getStatusListAnime(): List<Long> {
        return listOf(WATCHING, PLAN_TO_WATCH, COMPLETED, REWATCHING, ON_HOLD, DROPPED)
    }

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> MR.strings.watching
        PLAN_TO_WATCH -> MR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        REWATCHING -> MR.strings.repeating_anime
        ON_HOLD -> MR.strings.paused
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> {
        return when (scorePreference.get()) {
            // 10 point
            POINT_10 -> IntRange(0, 10).map(Int::toString).toImmutableList()
            // 100 point
            POINT_100 -> IntRange(0, 100).map(Int::toString).toImmutableList()
            // 5 stars
            POINT_5 -> IntRange(0, 5).map { "$it ★" }.toImmutableList()
            // Smiley
            POINT_3 -> persistentListOf("-", "😦", "😐", "😊")
            // 10 point decimal
            POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }.toImmutableList()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun get10PointScore(track: DomainAnimeTrack): Double {
        // Score is stored in 100 point format
        return track.score / 10.0
    }

    override fun indexToScore(index: Int): Double {
        return when (scorePreference.get()) {
            // 10 point
            POINT_10 -> index * 10.0
            // 100 point
            POINT_100 -> index.toDouble()
            // 5 stars
            POINT_5 -> when (index) {
                0 -> 0.0
                else -> index * 20.0 - 10.0
            }
            // Smiley
            POINT_3 -> when (index) {
                0 -> 0.0
                else -> index * 25.0 + 10.0
            }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toDouble()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: DomainAnimeTrack): String {
        val score = track.score

        return when (scorePreference.get()) {
            POINT_5 -> when (score) {
                0.0 -> "0 ★"
                else -> "${((score + 10) / 20).toInt()} ★"
            }
            POINT_3 -> when {
                score == 0.0 -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }
            else -> track.toApiScore()
        }
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track)
    }

    override suspend fun update(track: Track, didWatchEpisode: Boolean): Track {
        // Always refresh this ID. Older rows may contain an AniList media ID
        // or a stale list-entry ID, both of which make SaveMediaListEntry fail
        // with HTTP 400 when only the status is changed.
        val libAnime = api.findLibAnime(track, getUsername().toInt())
            ?: throw Exception("$track not found on user library")
        track.library_id = libAnime.library_id

        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainAnimeTrack) {
        if (track.libraryId == null || track.libraryId!! == 0L) {
            val libAnime = api.findLibAnime(track.toDbTrack(), getUsername().toInt()) ?: return
            return api.deleteLibAnime(track.copy(id = libAnime.library_id!!))
        }

        api.deleteLibAnime(track)
    }

    override suspend fun bind(track: Track, hasSeenEpisodes: Boolean): Track {
        val remoteTrack = api.findLibAnime(track, getUsername().toInt())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REWATCHING
                track.status = if (!isRereading && hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun searchAnime(query: String): List<TrackSearch> {
        return api.searchAnime(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibAnime(track, getUsername().toInt())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    private val relationsCache = java.util.concurrent.ConcurrentHashMap<Long, List<ALRelationEdge>>()

    override suspend fun getAnimeRelations(track: eu.kanade.tachiyomi.data.database.models.Track): List<ALRelationEdge> {
        return getAnimeRelations(track.remote_id)
    }

    suspend fun getAnimeRelations(trackId: Long): List<ALRelationEdge> {
        val cached = relationsCache[trackId]
        if (cached != null) return cached

        // Relation metadata is public and independent of the AniList account. Keep the last
        // successful result on disk so a temporary AniList outage does not remove the cards
        // from an already-tracked anime after the app is restarted.
        val stored = preferenceStore
            .getString(Preference.appStateKey("anilist_relations_$trackId"))
            .get()
            .takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { json.decodeFromString<List<ALRelationEdge>>(raw) }.getOrNull()
            }
        if (stored != null) {
            relationsCache[trackId] = stored
            return stored
        }

        val fetched = api.getRelations(trackId.toInt())
        if (fetched.isNotEmpty()) {
            relationsCache[trackId] = fetched
            preferenceStore
                .getString(Preference.appStateKey("anilist_relations_$trackId"))
                .set(json.encodeToString(fetched))
        }
        return fetched
    }

    /**
     * Resolves an untracked anime only when AniList has one exact title match.
     *
     * The resolved media ID is then handled by the same persistent relation cache as tracked
     * anime. An empty result is intentional when AniList returns no exact or more than one exact
     * title match; relation cards must not be populated from a guessed search result.
     */
    suspend fun getAnimeRelationsByTitle(title: String): List<ALRelationEdge> {
        val mediaId = api.findMediaIdByTitle(title) ?: return emptyList()
        return getAnimeRelations(mediaId.toLong())
    }

    suspend fun getUserAnimeList(): List<eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListItem> {
        return api.getUserAnimeList(getUsername().toInt())
    }


    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(token: String) {
        try {
            val oauth = api.createOAuth(token)
            interceptor.setAuth(oauth)
            val (username, scoreType) = api.getCurrentUser()
            scorePreference.set(scoreType)
            saveCredentials(username.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            logout()
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
        relationsCache.clear()
    }

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata? {
        return api.getAnimeMetadata(track)
    }

    override suspend fun fetchCastByTitle(remoteId: Long, mediaType: String): List<Credit>? {
        return api.fetchCastById(remoteId)
    }

    suspend fun fetchCastForAnimeTitle(title: String): List<Credit>? {
        val matches = api.searchAnime(title)
        val match = matches.firstOrNull { it.title.equals(title, ignoreCase = true) }
            ?: matches.firstOrNull()
        return match?.remote_id
            ?.takeIf { it > 0L }
            ?.let { api.fetchCastById(it) }
    }

    fun saveOAuth(alOAuth: ALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(alOAuth))
    }

    fun loadOAuth(): ALOAuth? {
        return try {
            json.decodeFromString<ALOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }

    override fun getNoticeStringRes(): StringResource {
        return MR.strings.anilist_import_notice
    }

    override suspend fun getImportableList(): List<ImportableEntry> {
        return getUserAnimeList().map { item ->
            val mappedStatusFilter = when (item.status) {
                "CURRENT", "REPEATING" -> ImportStatusFilter.WATCHING
                "PLANNING" -> ImportStatusFilter.PLAN_TO_WATCH
                "COMPLETED" -> ImportStatusFilter.COMPLETED
                "PAUSED" -> ImportStatusFilter.ON_HOLD
                else -> null
            }
            val alUserAnime = item.toALUserAnime()
            ImportableEntry(
                remoteId = item.media.id,
                title = item.media.title.userPreferred ?: "",
                coverUrl = item.media.coverImage.large ?: "",
                totalEpisodes = (item.media.episodes ?: 0).toLong(),
                episodesSeen = item.progress,
                score = item.scoreRaw.toDouble(),
                status = alUserAnime.toTrack().status,
                statusFilter = mappedStatusFilter,
                startDate = item.startedAt.toEpochMilli(),
                finishDate = item.completedAt.toEpochMilli(),
                trackingUrl = AnilistApi.animeUrl(item.media.id)
            )
        }
    }
}
