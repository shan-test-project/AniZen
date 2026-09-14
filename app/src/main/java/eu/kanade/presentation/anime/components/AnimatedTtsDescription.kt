package eu.kanade.presentation.anime.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import eu.kanade.tachiyomi.util.tts.AnimeDescriptionTtsController
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
    isSpeaking: Boolean,
    activeRange: IntRange?,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle,
) {
    if (!effectsEnabled || !isSpeaking) {
        BasicText(text = text, modifier = modifier, style = style)
        return
    }

    val safeStart = activeRange?.first?.coerceIn(0, text.length) ?: 0
    val safeEnd = activeRange?.last
        ?.plus(1)
        ?.coerceIn(safeStart, text.length)
        ?: safeStart
    val baseColor = if (style.color == Color.Unspecified) {
        LocalContentColor.current
    } else {
        style.color
    }
    val transition = rememberInfiniteTransition(label = "tts-wave")
    val activeAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tts-active-alpha",
    )
    val dimColor = baseColor.copy(alpha = baseColor.alpha * 0.16f)
    val activeColor = baseColor.copy(alpha = baseColor.alpha * activeAlpha)
    val annotatedText = buildAnnotatedString {
        withStyle(SpanStyle(color = dimColor)) {
            append(text)
        }
        if (safeEnd > safeStart) {
            addStyle(
                style = SpanStyle(
                    color = activeColor,
                    textDecoration = TextDecoration.Underline,
                ),
                start = safeStart,
                end = safeEnd,
            )
        }
    }
    BasicText(
        text = annotatedText,
        modifier = modifier,
        style = style,
    )
}
