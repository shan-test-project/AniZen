package mihon.feature.airingschedule

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        response.use {
            if (!isSuccessful) throw HttpException(code)

            val entries = LiveChartScheduleParser.parse(
                html = body.string(),
                weekStart = weekStart,
                weekEnd = weekEnd,
            )
            if (entries.isEmpty()) {
                throw IOException("LiveChart schedule returned no parseable entries")
            }
            return entries
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

    companion object {
        private const val SCHEDULE_URL =
            "https://www.livechart.me/schedule/all?sortby=airdate&layout=full"
        private const val MIN_REQUEST_INTERVAL_MS = 5_000L
        private val requestMutex = Mutex()
        private var lastRequestAt = 0L
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
                if (airingAt !in weekStart..weekEnd) return@mapNotNull null

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