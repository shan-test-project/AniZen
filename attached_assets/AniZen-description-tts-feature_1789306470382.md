# Feature spec: Text-to-Speech for anime description

Repo: `shan-test-project/AniZen`
Branch: `master`

Reads the anime description aloud on the anime details screen, with the
currently-spoken word underlined and given a gentle letter-wave animation,
synced as closely as Android's TTS callbacks allow.

## 1. New preferences: `TtsPreferences.kt`

Follow the exact pattern already used by
`app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt`
(same `PreferenceStore`-backed style, same file location convention — put
this one at
`app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/TtsPreferences.kt`
or alongside wherever `SubtitlePreferences` actually lives if it's not that
path — verify first, don't guess blind).

```kotlin
class TtsPreferences(private val preferenceStore: PreferenceStore) {
    fun enableTts() = preferenceStore.getBoolean("pref_tts_enabled", false)
    fun ttsSpeed() = preferenceStore.getFloat("pref_tts_speed", 1.0f)
    fun ttsVoiceName() = preferenceStore.getString("pref_tts_voice_name", "")
    fun autoPlaySummary() = preferenceStore.getBoolean("pref_tts_autoplay", false)
    fun enableVisualEffects() = preferenceStore.getBoolean("pref_tts_visual_effects", true)
}
```

`ttsSpeed()` allowed values: `0.8f`, `1.0f`, `1.2f`, `1.5f` — enforce this as
a fixed set of radio/segmented options in the settings UI, not a free slider.

Register it in the DI setup the same way `SubtitlePreferences` is registered
(find that registration and mirror it).

## 2. Settings UI — new "Text to Speech" section

Locate whichever settings screen currently hosts the "Subtitle"-related
toggles (likely a Player or Advanced settings screen) and add a sibling
section, following the existing Compose preference-row patterns in that
file (`SwitchPreference`, `ListPreference`, etc. — reuse whatever's already
used for Subtitle settings, don't introduce a new preference-row style):

- **Enable Text-to-Speech** — `SwitchPreference` bound to `enableTts()`.
- **Speech Speed** — a `ListPreference` (or segmented control if the
  existing style prefers one) with options `0.8x / 1.0x / 1.2x / 1.5x`,
  bound to `ttsSpeed()`, default `1.0x`.
- **Voice Selection** — a `ListPreference` populated at screen-open time from
  `TextToSpeech.getVoices()` (see §4), bound to `ttsVoiceName()`. If TTS is
  unavailable on the device (see §4), show this row disabled with an
  explanatory subtitle instead of hiding it silently.
- **Auto-play summary** — `SwitchPreference` bound to `autoPlaySummary()`,
  default off.
- **Enable visual effects** — `SwitchPreference` bound to
  `enableVisualEffects()`, default on.

All rows under "Enable Text-to-Speech" should be disabled (not hidden) when
that toggle is off, matching how dependent-preference-rows are already
disabled elsewhere in this codebase's settings screens.

## 3. TTS controller: `AnimeDescriptionTtsController.kt`

New file, e.g.
`app/src/main/java/eu/kanade/tachiyomi/util/tts/AnimeDescriptionTtsController.kt`.
Wraps Android's built-in `android.speech.tts.TextToSpeech`. This class owns
the engine lifecycle; the Compose layer only observes its state.

```kotlin
package eu.kanade.tachiyomi.util.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class AnimeDescriptionTtsController(context: Context) {

    enum class Availability { UNKNOWN, AVAILABLE, UNAVAILABLE }

    var availability by mutableStateOf(Availability.UNKNOWN)
        private set
    var isSpeaking by mutableStateOf(false)
        private set

    /** Character range [start, end) within the text CURRENTLY BEING SPOKEN, or null when idle. */
    var activeRange by mutableStateOf<IntRange?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            availability = if (status == TextToSpeech.SUCCESS) {
                Availability.AVAILABLE
            } else {
                Availability.UNAVAILABLE
            }
        }
    }

    fun availableVoices(): List<Voice> = tts?.voices?.toList().orEmpty()

    fun speak(text: String, speed: Float, voiceName: String?) {
        val engine = tts ?: return
        if (availability != Availability.AVAILABLE || text.isBlank()) return

        stop() // clear any previous utterance/state first

        engine.setSpeechRate(speed)
        if (!voiceName.isNullOrBlank()) {
            engine.voices.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
        }

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != currentUtteranceId) return
                isSpeaking = true
            }

            // API 26+. This is the actual per-word/phrase progress callback —
            // start/end are character offsets into the text passed to speak().
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != currentUtteranceId) return
                activeRange = start until end
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != currentUtteranceId) return
                isSpeaking = false
                activeRange = null
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != currentUtteranceId) return
                isSpeaking = false
                activeRange = null
            }
        })

        val params = Bundle()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        currentUtteranceId = null
        isSpeaking = false
        activeRange = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
```

Notes:
- `onRangeStart` requires API 26 (Android 8.0). If `minSdk` is lower, guard
  with `Build.VERSION.SDK_INT >= 26`; below that, visual sync isn't possible
  — audio still plays, just skip highlighting (this satisfies "gracefully
  handle devices that don't support TTS" for the sync feature specifically,
  separate from the engine-missing case).
- Engine-missing / no TTS data installed on the device surfaces as
  `Availability.UNAVAILABLE` from the constructor callback — the Play button
  must not render at all in that case (see §5), not just be disabled.
- All callback fields are `mutableStateOf`, so Compose recomposes correctly,
  but `UtteranceProgressListener` callbacks fire on a TTS-internal thread —
  confirm this actually reaches Compose safely; if not, wrap each field
  write in `Handler(Looper.getMainLooper()).post { ... }` or route through a
  `Channel`/`MutableStateFlow` collected with `collectAsState()` instead of
  raw `mutableStateOf`, to be safe with Compose's thread expectations.

## 4. Compose: animated text + play control

New file, e.g.
`app/src/main/java/eu/kanade/presentation/anime/components/AnimatedTtsDescription.kt`.

```kotlin
@Composable
fun rememberAnimeDescriptionTtsController(): AnimeDescriptionTtsController {
    val context = LocalContext.current
    val controller = remember { AnimeDescriptionTtsController(context) }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }
    return controller
}

@Composable
fun TtsPlayButton(
    visible: Boolean,
    isSpeaking: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return // "Only show the Play button if the description is not empty" +
                          // controller.availability == AVAILABLE, checked by the caller
    IconButton(onClick = { if (isSpeaking) onStop() else onPlay() }, modifier = modifier) {
        Icon(
            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isSpeaking) "Stop reading" else "Read description aloud",
        )
    }
}

/**
 * Renders [text] with the word/phrase inside [activeRange] underlined and,
 * when [effectsEnabled], given a gentle per-letter wave animation. Falls
 * back to plain static text with no wave when effects are disabled or
 * nothing is active — this must stay cheap since it can render on every
 * TTS progress tick.
 */
@Composable
fun AnimatedTtsText(
    text: String,
    activeRange: IntRange?,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    if (activeRange == null || !effectsEnabled) {
        Text(text = text, modifier = modifier, style = style)
        return
    }

    // Wave: each letter inside activeRange gets its own small vertical
    // offset driven by a shared infinite transition, phase-shifted by
    // character index so it reads as a traveling wave, not a flicker.
    val infiniteTransition = rememberInfiniteTransition(label = "tts-wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "tts-wave-phase",
    )

    Row(modifier = modifier) {
        // Text before the active range, plain.
        if (activeRange.first > 0) {
            Text(text = text.substring(0, activeRange.first), style = style)
        }
        // Active range: per-character wave + underline + color emphasis.
        text.substring(activeRange.first, (activeRange.last + 1).coerceAtMost(text.length))
            .forEachIndexed { index, char ->
                val yOffset = (sin(wavePhase + index * 0.6f) * 3f) // dp-ish magnitude, tune visually
                Text(
                    text = char.toString(),
                    style = style.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.offset(y = yOffset.dp),
                )
            }
        // Text after the active range, plain.
        if (activeRange.last + 1 < text.length) {
            Text(text = text.substring((activeRange.last + 1).coerceAtMost(text.length)), style = style)
        }
    }
}
```

This per-character `Row` of individual `Text` composables is fine for a
single active word/phrase (a handful of characters at a time) but do **not**
apply this per-character splitting to the *entire* description — only to the
substring inside `activeRange`. That's what keeps "performance smooth even
on longer descriptions" (requirement §4): the rest of the text is one plain
`Text` call, not one composable per letter.

## 5. Wiring into the anime screen

`ExpandableAnimeDescription` (in
`app/src/main/java/eu/kanade/presentation/anime/components/`) is the
existing composable rendering `state.anime.description` — confirm exact file
name first, it's referenced from `AnimeScreen.kt` as
`eu.kanade.presentation.anime.components.ExpandableAnimeDescription`.

- Add the `TtsPlayButton` next to it (e.g. in its header row).
- Button visibility: `state.anime.description?.isNotBlank() == true && ttsPreferences.enableTts().get() && controller.availability == Availability.AVAILABLE`.
- On Play: `controller.speak(state.anime.description, ttsPreferences.ttsSpeed().get(), ttsPreferences.ttsVoiceName().get())`.
- On Stop: `controller.stop()`.
- Pass `controller.activeRange` and `ttsPreferences.enableVisualEffects().get()` into `AnimatedTtsText` in place of the plain `Text` currently rendering the description.
- **Auto-play**: `LaunchedEffect(state.anime.id) { if (ttsPreferences.autoPlaySummary().get()) controller.speak(...) }` — key on anime id so it only fires once per anime, not on every recomposition.
- **Leaving the screen**: `rememberAnimeDescriptionTtsController()`'s `DisposableEffect` already calls `shutdown()` on dispose, which covers navigating away. Also call `controller.stop()` explicitly in `AnimeScreenModel`'s screen-model-cleared hook if one exists (mirror how other per-screen resources are torn down in that ScreenModel), so speech stops immediately rather than only when the whole controller disposes.

## 6. Acceptance criteria

1. Play button is absent (not just disabled) when the description is empty,
   or when the device has no usable TTS engine.
2. Tapping Play starts speech from the beginning; tapping Stop immediately
   halts audio and clears `activeRange` (removing underline + wave).
3. While speaking, the currently-spoken word/phrase is underlined; if
   "Enable visual effects" is off, audio still plays but no underline or
   wave renders.
4. Navigating away from the anime screen while speech is playing stops
   audio and clears effects — verify by starting playback, pressing back,
   and confirming no audio continues.
5. Speed setting (0.8x/1.0x/1.2x/1.5x) and voice selection are respected on
   the next Play call after being changed in Settings.
6. Auto-play summary, when enabled, starts speech automatically once when
   the anime screen opens with a non-empty description, and does not
   re-trigger on unrelated recompositions (scrolling, rotating, etc.).
7. On a device/emulator with no TTS engine installed, the feature fails
   gracefully — no crash, Play button doesn't appear, and this is verified
   by testing with the TTS engine's app data cleared or an emulator image
   with no engine installed.
