package eu.kanade.tachiyomi.ui.player.utils

private val subtitleExtensions = setOf("ass", "ssa", "srt", "vtt", "sub")

/**
 * Jimaku's file API has no language field. Prefer an explicitly labelled English file when one
 * exists, but allow unlabelled files because Jimaku commonly provides Japanese subtitle files
 * without an `en` token in the filename.
 */
internal fun isExplicitlyEnglishSubtitle(filename: String): Boolean {
    val tokens = filename
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() }

    return tokens.any { it == "en" || it == "eng" || it == "english" }
}

internal fun isSupportedSubtitleFile(filename: String): Boolean {
    return filename.substringAfterLast('.', "").lowercase() in subtitleExtensions
}

internal fun rankJimakuFile(filename: String): Int {
    return when {
        filename.lowercase().endsWith(".ass") -> 3
        filename.lowercase().endsWith(".ssa") -> 2
        filename.lowercase().endsWith(".srt") -> 1
        else -> 0
    }
}