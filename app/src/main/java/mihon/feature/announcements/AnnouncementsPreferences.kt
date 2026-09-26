package mihon.feature.announcements

import tachiyomi.core.common.preference.PreferenceStore

class AnnouncementsPreferences(private val preferenceStore: PreferenceStore) {
    fun cacheBlob() = preferenceStore.getString("announcements_cache_all", "")

    fun lastFetchedAt() = preferenceStore.getLong("announcements_last_fetched_all", 0L)

    fun watchlistMediaIds() =
        preferenceStore.getStringSet("announcement_watchlist_media_ids", emptySet())

    fun notifiedReleaseDateMediaIds() =
        preferenceStore.getStringSet("announcement_notified_release_date_ids", emptySet())
}