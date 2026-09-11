package eu.kanade.tachiyomi.ui.player.utils

private val englishSubtitleTokens = setOf("en", "eng", "english")
private val japaneseSubtitleTokens = setOf("ja", "jpn", "japanese", "nihongo")

/**
 * Jimaku's file API has no language field. Only accept files whose filenames explicitly identify
 * them as English, and never silently treat an unlabelled Japanese file as English.
 */
internal fun isExplicitlyEnglishSubtitle(filename: String): Boolean {
    val tokens = filename
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() }

    if (tokens.any { it in japaneseSubtitleTokens }) return false
    return tokens.any { it in englishSubtitleTokens }
}

internal fun rankJimakuFile(filename: String): Int {
    return when {
        filename.lowercase().endsWith(".ass") -> 3
        filename.lowercase().endsWith(".ssa") -> 2
        filename.lowercase().endsWith(".srt") -> 1
        else -> 0
    }
}