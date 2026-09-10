package eu.kanade.tachiyomi.ui.player.utils

internal fun rankJimakuFile(filename: String): Int {
    return when {
        filename.lowercase().endsWith(".ass") -> 3
        filename.lowercase().endsWith(".ssa") -> 2
        filename.lowercase().endsWith(".srt") -> 1
        else -> 0
    }
}