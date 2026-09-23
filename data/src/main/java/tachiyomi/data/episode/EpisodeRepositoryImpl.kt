package tachiyomi.data.episode

import kotlinx.coroutines.flow.Flow
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

class EpisodeRepositoryImpl(
    private val handler: DatabaseHandler,
) : EpisodeRepository {

    override suspend fun addAll(episodes: List<Episode>): List<Episode> {
        return handler.await(inTransaction = true) {
            episodes.map { episode ->
                // A library refresh and a manual refresh can observe the same missing
                // episode before either transaction writes it. The database constraint is
                // still the source of truth, so make this boundary idempotent instead of
                // turning a normal refresh race into a failed transaction.
                episodesQueries.insertOrIgnore(
                    episode.animeId,
                    episode.url,
                    episode.name,
                    episode.scanlator,
                    episode.seen,
                    episode.bookmark,
                    // AM (FILLERMARK) -->
                    episode.fillermark,
                    // <-- AM (FILLERMARK)
                    episode.lastSecondSeen,
                    episode.totalSeconds,
                    episode.episodeNumber,
                    episode.sourceOrder,
                    episode.dateFetch,
                    episode.dateUpload,
                    episode.summary,
                    episode.previewUrl,
                    episode.version,
                )

                // INSERT OR IGNORE may have found a row created by another refresh.
                // Return the persisted row in either case; an unexpected missing row is
                // a real database error and must not be silently converted to an empty list.
                episodesQueries.getEpisodeByUrlAndAnimeId(
                    episode.url,
                    episode.animeId,
                    EpisodeMapper::mapEpisode,
                ).executeAsOneOrNull()
                    ?: error(
                        "Episode insert was ignored but no row exists for " +
                            "animeId=${episode.animeId}, url=${episode.url}",
                    )
            }
        }
    }

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        handler.await(inTransaction = true) {
            episodeUpdates.forEach { episodeUpdate ->
                episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = episodeUpdate.fillermark,
                    // <-- AM (FILLERMARK)
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    summary = episodeUpdate.summary,
                    previewUrl = episodeUpdate.previewUrl,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        try {
            handler.await { episodesQueries.removeEpisodesWithIds(episodeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return handler.awaitList {
            episodesQueries.getEpisodesByAnimeId(animeId, applyScanlatorFilter.toLong(), EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getScanlatorsByAnimeId(animeId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override suspend fun getExcludedScanlatorsByAnimeId(animeId: Long): List<String> {
        return handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(animeId)
        }
    }

    override fun getExcludedScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(animeId)
        }
    }

    override suspend fun insertExcludedScanlator(animeId: Long, scanlator: String) {
        handler.await {
            excluded_scanlatorsQueries.insert(animeId, scanlator)
        }
    }

    override suspend fun removeExcludedScanlators(animeId: Long, scanlators: List<String>) {
        handler.await {
            excluded_scanlatorsQueries.remove(animeId, scanlators)
        }
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return handler.awaitList {
            episodesQueries.getBookmarkedEpisodesByAnimeId(
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    // AM (FILLERMARK) -->
    override suspend fun getFillermarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return handler.awaitList { episodesQueries.getFillermarkedEpisodesByAnimeId(animeId, EpisodeMapper::mapEpisode) }
    }
    // <-- AM (FILLERMARK)

    override suspend fun getEpisodeById(id: Long): Episode? {
        return handler.awaitOneOrNull { episodesQueries.getEpisodeById(id, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean): Flow<List<Episode>> {
        return handler.subscribeToList {
            episodesQueries.getEpisodesByAnimeId(animeId, applyScanlatorFilter.toLong(), EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return handler.awaitOneOrNull {
            episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    // SY -->
    override suspend fun getEpisodeByUrl(url: String): List<Episode> {
        return handler.awaitList { episodesQueries.getEpisodeByUrl(url, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getMergedEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return handler.awaitList {
            episodesQueries.getMergedEpisodesByAnimeId(
                animeId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getMergedEpisodeByAnimeIdAsFlow(
        animeId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Episode>> {
        return handler.subscribeToList {
            episodesQueries.getMergedEpisodesByAnimeId(
                animeId,
                applyScanlatorFilter.toLong(),
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getScanlatorsByMergeId(animeId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByMergeId(animeId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByMergeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByMergeId(animeId) { it.orEmpty() }
        }
    }
    // SY <--
}
