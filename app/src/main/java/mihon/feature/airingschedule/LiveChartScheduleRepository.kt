package mihon.feature.airingschedule

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import java.io.IOException

/**
 * Fallback schedule source used when AniList is unavailable.
 *
 * LiveChart does not expose AniList-compatible media IDs or a documented schedule API, so
 * the parser namespaces its anime IDs as negative values. Title matching remains the source
 * of truth when connecting fallback entries to the user's library.
 */
class LiveChartScheduleRepository {

    private val networkHelper: NetworkHelper by injectLazy()
    private val client get() = networkHelper.client

    suspend fun getWeeklySchedule(
        weekStart: Long,
        weekEnd: Long,
    ): List<AiringScheduleEntry> {
        rateLimit()

        val request = Request.Builder()
            .url(SCHEDULE_URL)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.8")
            .header("Referer", "https://www.livechart.me/")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .build()

        val response = client.newCall(request).await()
        response.use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)

            val entries = LiveChartScheduleParser.parse(
                html = response.body.string(),
                weekStart = weekStart,
                weekEnd = weekEnd,
            )
            if (entries.isEmpty()) {
                throw IOException("LiveChart schedule returned no parseable entries")
            }
            return enrichWithEnglishTitles(entries)
        }
    }

    private suspend fun enrichWithEnglishTitles(
        entries: List<AiringScheduleEntry>,
    ): List<AiringScheduleEntry> = coroutineScope {
        val semaphore = Semaphore(ENGLISH_TITLE_CONCURRENCY)
        entries.map { entry ->
            async {
                val originalTitles = (
                    entry.titleAliases +
                        listOfNotNull(
                            entry.titleUserPreferred,
                            entry.titleEnglish,
                            entry.titleRomaji,
                            entry.titleNative,
                        )
                    ).distinct()
                val englishTitle = semaphore.withPermit {
                    fetchEnglishTitle(-entry.mediaId)
                } ?: entry.titleUserPreferred

                entry.copy(
                    titleUserPreferred = englishTitle,
                    titleEnglish = englishTitle,
                    titleRomaji = englishTitle,
                    titleNative = englishTitle,
                    titleAliases = (originalTitles + englishTitle).distinct(),
                )
            }
        }.awaitAll()
    }

    private suspend fun fetchEnglishTitle(liveChartAnimeId: Int): String? {
        if (liveChartAnimeId <= 0) return null
        titleRateLimit()

        val request = Request.Builder()
            .url("https://www.livechart.me/anime/$liveChartAnimeId")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.8")
            .header("Referer", SCHEDULE_URL)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .build()

        return try {
            val response = client.newCall(request).await()
            response.use { response ->
                if (!response.isSuccessful) return null
                val document = Jsoup.parse(response.body.string(), "https://www.livechart.me/")
                document
                    .selectFirst("[data-anime-details-english-title]")
                    ?.attr("data-anime-details-english-title")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: document
                        .selectFirst("[data-anime-details-non-preferred-title]")
                        ?.attr("data-anime-details-non-preferred-title")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun rateLimit() {
        requestMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestAt
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private suspend fun titleRateLimit() {
        titleRequestMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastTitleRequestAt
            if (elapsed < MIN_TITLE_REQUEST_INTERVAL_MS) {
                delay(MIN_TITLE_REQUEST_INTERVAL_MS - elapsed)
            }
            lastTitleRequestAt = System.currentTimeMillis()
        }
    }

    companion object {
        private const val SCHEDULE_URL =
            "https://www.livechart.me/schedule/all?sortby=airdate&layout=full"
        private const val MIN_REQUEST_INTERVAL_MS = 5_000L
        private const val MIN_TITLE_REQUEST_INTERVAL_MS = 250L
        private const val ENGLISH_TITLE_CONCURRENCY = 4
        private val requestMutex = Mutex()
        private val titleRequestMutex = Mutex()
        private var lastRequestAt = 0L
        private var lastTitleRequestAt = 0L
    }
}

internal object LiveChartScheduleParser {

    private val episodeRegex = Regex("""\bEP\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val scheduleIdRegex = Regex("""/schedules/(\d+)""")

    fun parse(
        html: String,
        weekStart: Long,
        weekEnd: Long,
    ): List<AiringScheduleEntry> {
        val document = Jsoup.parse(html, "https://www.livechart.me/")

        return document
            .select("div.lc-timetable-anime-block[data-schedule-anime-id]")
            .mapNotNull { element ->
                val liveChartAnimeId = element
                    .attr("data-schedule-anime-id")
                    .toIntOrNull()
                    ?: return@mapNotNull null
                val airingAt = element
                    .attr("data-schedule-anime-release-date-value")
                    .toLongOrNull()
                    ?: return@mapNotNull null
                // [weekStart, weekEnd) matches AniList's airingAt_greater/airingAt_lesser
                // range and prevents an event exactly at next Monday midnight from leaking
                // into the previous week.
                if (airingAt < weekStart || airingAt >= weekEnd) return@mapNotNull null

                val title = element
                    .attr("data-schedule-anime-title")
                    .trim()
                    .ifBlank {
                        element.selectFirst("a.lc-tt-anime-title")
                            ?.attr("title")
                            .orEmpty()
                            .trim()
                    }
                if (title.isBlank()) return@mapNotNull null

                val releaseLabel = element
                    .selectFirst(".lc-tt-release-label")
                    ?.text()
                    .orEmpty()
                val episode = episodeRegex
                    .find(releaseLabel)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                val scheduleId = element
                    .select("a[href*='/schedules/']")
                    .firstOrNull()
                    ?.attr("href")
                    ?.let { scheduleIdRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    ?: liveChartAnimeId
                val coverImageUrl = element
                    .selectFirst("img[data-schedule-anime-target='poster']")
                    ?.absUrl("src")
                    .orEmpty()
                val totalEpisodes = element
                    .attr("data-schedule-anime-total-episodes-value")
                    .toIntOrNull()

                AiringScheduleEntry(
                    scheduleId = scheduleId,
                    airingAt = airingAt,
                    episode = episode,
                    mediaId = -liveChartAnimeId,
                    titleUserPreferred = title,
                    // LiveChart exposes one preferred display title rather than AniList's
                    // three title fields. Treat that value as the available title for every
                    // language choice instead of silently forcing the Romaji branch.
                    titleEnglish = title,
                    titleRomaji = title,
                    coverImageUrl = coverImageUrl,
                    totalEpisodes = totalEpisodes,
                    isAdult = false,
                )
            }
            .distinctBy { it.scheduleId }
            .sortedBy { it.airingAt }
    }
}