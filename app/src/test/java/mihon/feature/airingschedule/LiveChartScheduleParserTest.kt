package mihon.feature.airingschedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveChartScheduleParserTest {

    @Test
    fun `parses schedule blocks into the shared airing model`() {
        val entries = LiveChartScheduleParser.parse(
            html = """
                <div class="lc-timetable-anime-block"
                    data-schedule-anime-id="123"
                    data-schedule-anime-title="Example Anime"
                    data-schedule-anime-release-date-value="1500"
                    data-schedule-anime-total-episodes-value="24">
                    <img data-schedule-anime-target="poster" src="/posters/example.jpg">
                    <a class="lc-tt-anime-title" title="Example Anime" href="/anime/123">Example Anime</a>
                    <a class="lc-tt-release-label" href="/anime/123/schedules/456">
                        <span class="font-medium">EP12</span> · TV (JP)
                    </a>
                </div>
            """.trimIndent(),
            weekStart = 1000,
            weekEnd = 2000,
        )

        assertEquals(1, entries.size)
        assertEquals(
            AiringScheduleEntry(
                scheduleId = 456,
                airingAt = 1500,
                episode = 12,
                mediaId = -123,
                titleUserPreferred = "Example Anime",
                titleEnglish = "Example Anime",
                titleRomaji = "Example Anime",
                coverImageUrl = "https://www.livechart.me/posters/example.jpg",
                totalEpisodes = 24,
            ),
            entries.single(),
        )
    }

    @Test
    fun `filters entries outside requested week and keeps entries without episode metadata`() {
        val entries = LiveChartScheduleParser.parse(
            html = """
                <div class="lc-timetable-anime-block"
                    data-schedule-anime-id="1"
                    data-schedule-anime-title="Before"
                    data-schedule-anime-release-date-value="999">
                    <a class="lc-tt-release-label" href="/anime/1/schedules/10">EP1</a>
                </div>
                <div class="lc-timetable-anime-block"
                    data-schedule-anime-id="2"
                    data-schedule-anime-title="Movie Event"
                    data-schedule-anime-release-date-value="1500">
                    <a class="lc-tt-release-label" href="/anime/2/schedules/20">Movie · TV (JP)</a>
                </div>
                <div class="lc-timetable-anime-block"
                    data-schedule-anime-id="3"
                    data-schedule-anime-title="After"
                    data-schedule-anime-release-date-value="2001">
                    <a class="lc-tt-release-label" href="/anime/3/schedules/30">EP2</a>
                </div>
            """.trimIndent(),
            weekStart = 1000,
            weekEnd = 2000,
        )

        assertEquals(1, entries.size)
        assertEquals("Movie Event", entries.single().titleUserPreferred)
        assertEquals(0, entries.single().episode)
        assertTrue(entries.single().mediaId < 0)
    }

    @Test
    fun `excludes the exclusive next-week boundary`() {
        val entries = LiveChartScheduleParser.parse(
            html = """
                <div class="lc-timetable-anime-block"
                    data-schedule-anime-id="1"
                    data-schedule-anime-title="Next Week"
                    data-schedule-anime-release-date-value="2000">
                    <a class="lc-tt-release-label" href="/anime/1/schedules/10">EP1</a>
                </div>
            """.trimIndent(),
            weekStart = 1000,
            weekEnd = 2000,
        )

        assertTrue(entries.isEmpty())
    }
}