package mihon.feature.airingschedule

import kotlinx.serialization.Serializable

@Serializable
data class AiringScheduleEntry(
    val scheduleId: Int,
    val airingAt: Long,
    val episode: Int,
    val mediaId: Int,
    val titleUserPreferred: String,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val coverImageUrl: String = "",
    val totalEpisodes: Int? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
) {
    fun displayTitle(language: SchedulePreferences.TitleLanguage): String = when (language) {
        SchedulePreferences.TitleLanguage.ENGLISH ->
            titleEnglish?.takeIf { it.isNotBlank() }
                ?: titleUserPreferred.takeIf { it.isNotBlank() }
                ?: titleRomaji?.takeIf { it.isNotBlank() }
                ?: titleNative?.takeIf { it.isNotBlank() }
                .orEmpty()
        SchedulePreferences.TitleLanguage.ROMAJI ->
            titleRomaji?.takeIf { it.isNotBlank() }
                ?: titleUserPreferred.takeIf { it.isNotBlank() }
                ?: titleEnglish?.takeIf { it.isNotBlank() }
                ?: titleNative?.takeIf { it.isNotBlank() }
                .orEmpty()
        SchedulePreferences.TitleLanguage.NATIVE ->
            titleNative?.takeIf { it.isNotBlank() }
                ?: titleUserPreferred.takeIf { it.isNotBlank() }
                ?: titleEnglish?.takeIf { it.isNotBlank() }
                ?: titleRomaji?.takeIf { it.isNotBlank() }
                .orEmpty()
        SchedulePreferences.TitleLanguage.USER_PREFERRED ->
            titleUserPreferred.takeIf { it.isNotBlank() }
                ?: titleEnglish?.takeIf { it.isNotBlank() }
                ?: titleRomaji?.takeIf { it.isNotBlank() }
                ?: titleNative?.takeIf { it.isNotBlank() }
                .orEmpty()
    }

    fun hasAired(): Boolean = airingAt <= System.currentTimeMillis() / 1000L
}
