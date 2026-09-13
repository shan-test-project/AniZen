package eu.kanade.tachiyomi.ui.player.settings

import tachiyomi.core.common.preference.PreferenceStore

class TtsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enableTts() = preferenceStore.getBoolean("pref_tts_enabled", false)
    fun ttsSpeed() = preferenceStore.getFloat("pref_tts_speed", 1.0f)
    fun ttsVoiceName() = preferenceStore.getString("pref_tts_voice_name", "")
    fun autoPlaySummary() = preferenceStore.getBoolean("pref_tts_autoplay", false)
    fun enableVisualEffects() = preferenceStore.getBoolean("pref_tts_visual_effects", true)
}