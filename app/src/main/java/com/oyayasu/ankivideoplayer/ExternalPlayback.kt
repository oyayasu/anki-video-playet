package com.oyayasu.ankivideoplayer

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

data class IncomingPlayback(
    val uri: Uri,
    val title: String,
    val positionMs: Long,
    val mimeHint: String?
)

object ExternalPlayback {
    fun parse(intent: Intent?): IncomingPlayback? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme !in setOf("http", "https", "file", "content")) return null
        val title = listOf("title", Intent.EXTRA_TITLE, "video_title", "name")
            .firstNotNullOfOrNull { intent.getStringExtra(it)?.takeIf(String::isNotBlank) }
            ?: uri.lastPathSegment?.substringBeforeLast('.') ?: "Vídeo externo"
        val position = try {
            val data = intent.extras
            when (val raw = data?.get("position") ?: data?.get("startfrom")) {
                is Long -> raw
                is Int -> raw.toLong()
                is String -> raw.toLongOrNull() ?: 0L
                else -> 0L
            }
        } catch (_: Exception) { 0L }
        return IncomingPlayback(uri, title.take(160), position.coerceAtLeast(0), intent.type)
    }

    fun asMediaItem(uri: Uri, hint: String? = null): MediaItem {
        val path = uri.toString().substringBefore('?').lowercase()
        val mime = when {
            path.endsWith(".m3u8") || hint?.contains("mpegurl", true) == true ->
                MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") || hint?.contains("dash+xml", true) == true ->
                MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder().setUri(uri).apply {
            if (mime != null) setMimeType(mime)
        }.build()
    }
}
