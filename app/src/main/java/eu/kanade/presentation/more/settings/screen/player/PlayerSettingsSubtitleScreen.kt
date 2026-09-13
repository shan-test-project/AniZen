package eu.kanade.presentation.more.settings.screen.player

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.settings.TtsPreferences
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsSubtitleScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_subtitle

    @Composable
    override fun getPreferences(): List<Preference> {
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
        val ttsPreferences = remember { Injekt.get<TtsPreferences>() }
        val context = LocalContext.current
        var ttsEnabled by remember { mutableStateOf(ttsPreferences.enableTts().get()) }
        var voices by remember { mutableStateOf(emptyList<android.speech.tts.Voice>()) }

        DisposableEffect(context) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    voices = tts?.voices
                        .orEmpty()
                        .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
                }
            }
            onDispose { tts?.shutdown() }
        }

        val langPref = subtitlePreferences.preferredSubLanguages()
        val whitelist = subtitlePreferences.subtitleWhitelist()
        val blacklist = subtitlePreferences.subtitleBlacklist()
        val disableAutoSubtitles = subtitlePreferences.disableAutoSubtitles()
        val jimakuEnabled = subtitlePreferences.jimakuEnabled()
        val jimakuApiKey = subtitlePreferences.jimakuApiKey()
        val speedPref = ttsPreferences.ttsSpeed()
        val voicePref = ttsPreferences.ttsVoiceName()
        val autoPlayPref = ttsPreferences.autoPlaySummary()
        val visualEffectsPref = ttsPreferences.enableVisualEffects()
        val voiceEntries = remember(voices) {
            voices.associate { it.name to "${it.locale.displayName} — ${it.name}" }.toPersistentMap()
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = disableAutoSubtitles,
                title = stringResource(MR.strings.pref_disable_auto_subtitles),
                subtitle = stringResource(MR.strings.pref_disable_auto_subtitles_summary),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = langPref,
                title = stringResource(MR.strings.pref_player_subtitle_lang),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_lang_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = whitelist,
                title = stringResource(MR.strings.pref_player_subtitle_whitelist),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_whitelist_info),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = blacklist,
                title = stringResource(MR.strings.pref_player_subtitle_blacklist),
                dialogSubtitle = stringResource(MR.strings.pref_player_subtitle_blacklist_info),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = jimakuEnabled,
                title = stringResource(MR.strings.pref_jimaku_enabled),
                subtitle = stringResource(MR.strings.pref_jimaku_enabled_summary),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = jimakuApiKey,
                title = stringResource(MR.strings.pref_jimaku_api_key),
                dialogSubtitle = stringResource(MR.strings.pref_jimaku_api_key_info),
                enabled = jimakuEnabled.get(),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = ttsPreferences.enableTts(),
                title = stringResource(MR.strings.pref_tts_enabled),
                subtitle = stringResource(MR.strings.pref_tts_enabled_summary),
                onValueChanged = {
                    ttsEnabled = it
                    true
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = speedPref,
                title = stringResource(MR.strings.pref_tts_speed),
                entries = mapOf(
                    0.8f to "0.8x",
                    1.0f to "1.0x",
                    1.2f to "1.2x",
                    1.5f to "1.5x",
                ).toPersistentMap(),
                enabled = ttsEnabled,
            ),
            Preference.PreferenceItem.ListPreference(
                pref = voicePref,
                title = stringResource(MR.strings.pref_tts_voice),
                subtitle = if (voiceEntries.isEmpty()) {
                    stringResource(MR.strings.pref_tts_voice_unavailable)
                } else {
                    "%s"
                },
                entries = voiceEntries,
                enabled = ttsEnabled && voiceEntries.isNotEmpty(),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = autoPlayPref,
                title = stringResource(MR.strings.pref_tts_autoplay),
                subtitle = stringResource(MR.strings.pref_tts_autoplay_summary),
                enabled = ttsEnabled,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = visualEffectsPref,
                title = stringResource(MR.strings.pref_tts_visual_effects),
                subtitle = stringResource(MR.strings.pref_tts_visual_effects_summary),
                enabled = ttsEnabled,
            ),
        )
    }
}
