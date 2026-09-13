package eu.kanade.presentation.anime.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import eu.kanade.tachiyomi.util.tts.AnimeDescriptionTtsController
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.sin

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
    if (!visible) return
    IconButton(
        onClick = { if (isSpeaking) onStop() else onPlay() },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (isSpeaking) MR.strings.tts_stop_reading else MR.strings.tts_read_summary,
            ),
        )
    }
}

@Composable
fun AnimatedTtsText(
    text: String,
    activeRange: IntRange?,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle,
) {
    if (activeRange == null || !effectsEnabled) {
        BasicText(text = text, modifier = modifier, style = style)
        return
    }

    val safeStart = activeRange.first.coerceIn(0, text.length)
    val safeEnd = (activeRange.last + 1).coerceIn(safeStart, text.length)
    val density = LocalDensity.current.density
    val transition = rememberInfiniteTransition(label = "tts-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tts-wave-phase",
    )

    Row(modifier = modifier) {
        if (safeStart > 0) {
            BasicText(text.substring(0, safeStart), style = style)
        }
        text.substring(safeStart, safeEnd)
            .forEachIndexed { index, char ->
                val yOffset = sin(phase + index * 0.6f) * 3f
                BasicText(
                    text = AnnotatedString(
                        char.toString(),
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        translationY = yOffset * density
                    },
                )
            }
        if (safeEnd < text.length) {
            BasicText(text.substring(safeEnd), style = style)
        }
    }
}