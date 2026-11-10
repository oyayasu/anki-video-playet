package com.oyayasu.ankivideoplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

/** A player overlay with interactive subtitle words in both embedded and external tracks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackOverlay(
    player: ExoPlayer,
    title: String,
    position: Long,
    caption: CueLine?,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    onFullscreen: (Boolean) -> Unit,
    onFavorite: (CueLine) -> Unit,
    onWord: (String, CueLine) -> Unit
) {
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var controls by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var touchX by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var hint by remember { mutableStateOf("") }
    val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
    BackHandler(fullscreen) { onFullscreen(false) }

    DisposableEffect(fullscreen, activity) {
        if (fullscreen && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                 View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                 View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
        onDispose {
            if (fullscreen && activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    Box(
        modifier.background(Color.Black)
            .pointerInput(player, duration) {
                detectTapGestures(
                    onTap = { controls = !controls; hint = "" },
                    onDoubleTap = { tap ->
                        val delta = if (tap.x < size.width / 2) -10000L else 10000L
                        val next = (player.currentPosition + delta).coerceAtLeast(0)
                        player.seekTo(if (duration > 0) next.coerceAtMost(duration) else next)
                        hint = if (delta > 0) "+10 segundos" else "-10 segundos"
                        controls = true
                    }
                )
            }
            .pointerInput(player, duration) {
                detectDragGestures(
                    onDragStart = { start ->
                        touchX = start.x
                        dragX = 0f; dragY = 0f
                        brightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        dragX += delta.x; dragY += delta.y
                        if (abs(dragX) > abs(dragY) * 1.3f) {
                            hint = "Avançar/retroceder"
                        } else if (touchX < size.width / 2f && activity != null) {
                            brightness = (brightness - delta.y / size.height.coerceAtLeast(1)).coerceIn(.05f, 1f)
                            val attrs = activity.window.attributes
                            attrs.screenBrightness = brightness
                            activity.window.attributes = attrs
                            hint = "Brilho " + (brightness * 100).roundToInt().toString() + "%"
                        } else {
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val d = (-delta.y / size.height.coerceAtLeast(1) * max * 3f).roundToInt()
                            if (d != 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                                (now + d).coerceIn(0, max), 0)
                            hint = "Volume " + audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    },
                    onDragEnd = {
                        if (abs(dragX) > abs(dragY) * 1.3f && abs(dragX) > 25f) {
                            val next = (player.currentPosition + (dragX / size.width * 90000).toLong())
                                .coerceAtLeast(0)
                            player.seekTo(if (duration > 0) next.coerceAtMost(duration) else next)
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    subtitleView?.visibility = View.GONE
                }
            },
            update = {
                it.player = player
                it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )
        // Subtitle taps never require an external sidecar: Media3 embedded cues
        // feed the same caption value as the local SRT parser.
        if (caption != null && caption.text.isNotBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    start = 10.dp, end = 10.dp, bottom = if (controls) 94.dp else 18.dp
                ),
                color = Color(0xCC000000), shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.Center) {
                        caption.text.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach { token ->
                            Text(
                                token, color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.clickable { onWord(token, caption) }
                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                            )
                        }
                    }
                    TextButton(onClick = { onFavorite(caption) }, modifier = Modifier.align(Alignment.End)) {
                        Text("★ Favoritar", color = Color(0xFFACE5F0))
                    }
                }
            }
        }
        if (hint.isNotBlank() && controls) {
            Surface(modifier = Modifier.align(Alignment.Center), color = Color(0xBB000000),
                shape = RoundedCornerShape(12.dp)) {
                Text(hint, color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
        if (controls) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0xA0000000))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { zoom = !zoom }) {
                    Text(if (zoom) "Ajustar" else "Preencher", color = Color.White)
                }
                TextButton(onClick = { onFullscreen(!fullscreen) }) {
                    Text(if (fullscreen) "Sair ⤡" else "Tela cheia ⛶", color = Color.White)
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xB0000000))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                if (duration > 0) {
                    Slider(
                        value = (if (scrubbing) scrub else position.toFloat()).coerceIn(0f, duration.toFloat()),
                        valueRange = 0f..duration.toFloat(),
                        onValueChange = { scrubbing = true; scrub = it },
                        onValueChangeFinished = { player.seekTo(scrub.toLong()); scrubbing = false }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SavedLineFormat.time(position).substringBeforeLast('.'),
                        color = Color.White, style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }) {
                        Text("−10", color = Color.White)
                    }
                    TextButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Text(if (player.isPlaying) "Pausar" else "Reproduzir", color = Color.White)
                    }
                    TextButton(onClick = { player.seekTo(player.currentPosition + 10000) }) {
                        Text("+10", color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        speed = when(speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> .75f; else -> 1f }
                        player.setPlaybackSpeed(speed)
                    }) { Text(speed.toString() + "×", color = Color.White) }
                }
            }
        }
    }
}
