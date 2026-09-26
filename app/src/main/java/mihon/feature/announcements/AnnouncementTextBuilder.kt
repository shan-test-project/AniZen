package mihon.feature.announcements

object AnnouncementTextBuilder {

    fun sourceLabel(source: String?): String? = when (source) {
        "MANGA" -> "manga"
        "LIGHT_NOVEL" -> "light novel"
        "VISUAL_NOVEL" -> "visual novel"
        "GAME" -> "game"
        "WEB_NOVEL" -> "web novel"
        "ORIGINAL" -> null
        else -> null
    }

    fun formatLabel(format: String?): String = when (format) {
        "TV" -> "TV anime"
        "MOVIE" -> "movie"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "SPECIAL" -> "special"
        else -> "anime"
    }

    fun categoryFor(
        format: String?,
        source: String?,
        hasPrequelEdge: Boolean,
        currentTitle: String,
        relatedTitle: String?,
    ): AnnouncementCategory? {
        return when {
            format == "MOVIE" -> AnnouncementCategory.MOVIE
            hasPrequelEdge -> if (isSameFranchise(currentTitle, relatedTitle)) {
                AnnouncementCategory.NEW_SEASON
            } else {
                AnnouncementCategory.SEQUEL
            }
            source in ADAPTABLE_SOURCES -> AnnouncementCategory.ADAPTATION
            else -> null
        }
    }

    fun buildDescription(
        title: String,
        category: AnnouncementCategory,
        format: String?,
        source: String?,
        relatedTitle: String?,
        relatedYear: Int?,
        expectedYear: Int?,
    ): String {
        val fmt = formatLabel(format)
        return when (category) {
            AnnouncementCategory.ADAPTATION -> {
                val src = sourceLabel(source)
                if (src != null) {
                    "$title is getting a $fmt adaptation, based on the original $src."
                } else {
                    "$title is getting a $fmt adaptation."
                }
            }
            AnnouncementCategory.SEQUEL, AnnouncementCategory.NEW_SEASON -> {
                when {
                    relatedTitle != null && relatedYear != null ->
                        "A new season of $relatedTitle has been confirmed, continuing the story from $relatedYear."
                    relatedTitle != null ->
                        "A new season of $relatedTitle has been confirmed."
                    else ->
                        "$title has been confirmed for a new season."
                }
            }
            AnnouncementCategory.MOVIE -> {
                val yearClause = expectedYear?.let { ", expected in $it." } ?: "."
                "$title is getting a new $fmt$yearClause"
            }
        }
    }

    private fun isSameFranchise(currentTitle: String, relatedTitle: String?): Boolean {
        if (relatedTitle == null) return false
        val overlapWords = significantWords(currentTitle).intersect(significantWords(relatedTitle))
        val relatedWordCount = significantWords(relatedTitle).size
        if (relatedWordCount == 0) return false
        return overlapWords.size.toFloat() / relatedWordCount >= 0.5f
    }

    private fun significantWords(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() && it !in STOP_WORDS }
        .toSet()

    private val STOP_WORDS = setOf("the", "a", "an", "of", "in", "on", "season", "part", "final")
    private val ADAPTABLE_SOURCES =
        setOf("MANGA", "LIGHT_NOVEL", "VISUAL_NOVEL", "GAME", "WEB_NOVEL")
}