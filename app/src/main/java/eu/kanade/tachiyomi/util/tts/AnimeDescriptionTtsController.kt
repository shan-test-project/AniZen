package eu.kanade.tachiyomi.util.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class AnimeDescriptionTtsController(context: Context) {
    enum class Availability {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE,
    }

    var availability by mutableStateOf(Availability.UNKNOWN)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var activeRange by mutableStateOf<IntRange?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            mainHandler.post {
                availability = if (status == TextToSpeech.SUCCESS) {
                    Availability.AVAILABLE
                } else {
                    Availability.UNAVAILABLE
                }
            }
        }
    }

    fun availableVoices(): List<Voice> = tts?.voices?.toList().orEmpty()

    fun speak(text: String, speed: Float, voiceName: String?) {
        val engine = tts ?: return
        if (availability != Availability.AVAILABLE || text.isBlank()) return

        stop()
        engine.setSpeechRate(speed.coerceIn(0.8f, 1.5f))
        voiceName
            ?.takeIf(String::isNotBlank)
            ?.let { name -> engine.voices.firstOrNull { it.name == name } }
            ?.let { engine.voice = it }

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                updateOnMain(id) {
                    isSpeaking = true
                }
            }

            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                updateOnMain(id) {
                    activeRange = start until end
                }
            }

            override fun onDone(id: String?) {
                updateOnMain(id) {
                    isSpeaking = false
                    activeRange = null
                }
            }

            override fun onError(id: String?) {
                updateOnMain(id) {
                    isSpeaking = false
                    activeRange = null
                }
            }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
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

    private fun updateOnMain(id: String?, update: () -> Unit) {
        mainHandler.post {
            if (id == currentUtteranceId) update()
        }
    }
}