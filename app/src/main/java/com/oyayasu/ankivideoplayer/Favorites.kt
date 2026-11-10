package com.oyayasu.ankivideoplayer

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class SavedLine(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val season: String,
    val episode: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val subtitleStartMs: Long,
    val subtitleEndMs: Long,
    val savedAt: String = Instant.now().toString()
)

object SavedLineFormat {
    fun time(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d",
            t / 3600000, t / 60000 % 60, t / 1000 % 60, t % 1000)
    }

    fun identity(line: SavedLine): String = listOf(
        line.title.lowercase(Locale.ROOT).trim(), line.season.trim(), line.episode.trim(),
        line.subtitleStartMs.toString(), line.subtitleEndMs.toString(), line.text.trim()
    ).joinToString("\u001F")

    fun encode(lines: List<SavedLine>): String {
        val array = JSONArray()
        lines.forEach { line ->
            array.put(JSONObject()
                .put("id", line.id)
                .put("title", line.title)
                .put("season", line.season)
                .put("episode", line.episode)
                .put("text", line.text)
                .put("startMs", line.startMs)
                .put("endMs", line.endMs)
                .put("subtitleStartMs", line.subtitleStartMs)
                .put("subtitleEndMs", line.subtitleEndMs)
                .put("savedAt", line.savedAt))
        }
        return JSONObject()
            .put("format", "anki-video-player-favorites")
            .put("version", 1)
            .put("exportedAt", Instant.now().toString())
            .put("favorites", array)
            .toString(2)
    }

    fun decode(json: String): List<SavedLine> {
        val root = JSONObject(json.removePrefix("\uFEFF"))
        require(root.optString("format") == "anki-video-player-favorites" && root.optInt("version") == 1) {
            "Arquivo JSON incompatível com as favoritas do aplicativo."
        }
        val array = root.getJSONArray("favorites")
        require(array.length() <= 50000) { "Arquivo de favoritas muito grande." }
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            SavedLine(
                id = row.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = row.getString("title"),
                season = row.optString("season"),
                episode = row.optString("episode"),
                text = row.getString("text"),
                startMs = row.getLong("startMs").coerceAtLeast(0),
                endMs = row.getLong("endMs").coerceAtLeast(0),
                subtitleStartMs = row.getLong("subtitleStartMs").coerceAtLeast(0),
                subtitleEndMs = row.getLong("subtitleEndMs").coerceAtLeast(0),
                savedAt = row.optString("savedAt")
            )
        }
    }

    fun asText(lines: List<SavedLine>): String = buildString {
        appendLine("ANKI VIDEO PLAYER | LEGENDAS FAVORITAS")
        appendLine("Texto UTF-8; total de legendas: " + lines.size)
        appendLine("Exportado: " + Instant.now())
        val grouped = lines.groupBy { Triple(it.title, it.season, it.episode) }
        grouped.forEach { (metadata, entries) ->
            appendLine()
            appendLine("===================================================")
            appendLine("OBRA: " + metadata.first)
            if (metadata.second.isNotBlank()) appendLine("TEMPORADA: " + metadata.second)
            if (metadata.third.isNotBlank()) appendLine("EPISÓDIO: " + metadata.third)
            appendLine("===================================================")
            entries.sortedBy { it.startMs }.forEachIndexed { index, line ->
                appendLine()
                appendLine((index + 1).toString() + ". " + time(line.startMs) + " --> " + time(line.endMs))
                appendLine("   Legenda: " + time(line.subtitleStartMs) + " --> " + time(line.subtitleEndMs))
                appendLine("   " + line.text.replace("\n", "\n   "))
            }
        }
    }
}

class SavedLineStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "study_favorites", Context.MODE_PRIVATE
    )
    private val key = "saved_v1"

    fun load(): List<SavedLine> = try {
        val stored = preferences.getString(key, null)
        if (stored == null) emptyList() else SavedLineFormat.decode(stored)
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(items: List<SavedLine>): List<SavedLine> {
        check(preferences.edit().putString(key, SavedLineFormat.encode(items)).commit()) {
            "Falha ao salvar favoritos no dispositivo."
        }
        return items
    }

    fun add(line: SavedLine): List<SavedLine> {
        val old = load()
        return if (old.any { SavedLineFormat.identity(it) == SavedLineFormat.identity(line) }) {
            old
        } else persist(old + line)
    }

    fun remove(id: String): List<SavedLine> = persist(load().filterNot { it.id == id })

    fun restore(json: String): Pair<List<SavedLine>, Int> {
        val incoming = SavedLineFormat.decode(json)
        val current = load()
        val seen = current.mapTo(mutableSetOf()) { SavedLineFormat.identity(it) }
        val additions = incoming.filter { seen.add(SavedLineFormat.identity(it)) }
        return persist(current + additions) to additions.size
    }
}

@Composable
fun FavoriteSection(activeCue: CueLine?, offsetMs: Long, currentMedia: String) {
    val context = LocalContext.current
    val store = remember(context) { SavedLineStore(context) }
    var favorites by remember { mutableStateOf(store.load()) }
    var title by remember(currentMedia) { mutableStateOf(currentMedia) }
    var season by remember(currentMedia) { mutableStateOf("") }
    var episode by remember(currentMedia) { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    val exportTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(SavedLineFormat.asText(favorites).toByteArray(Charsets.UTF_8))
                } ?: error("Arquivo não criado.")
                result = "TXT exportado: " + favorites.size + " legendas."
            } catch (error: Exception) {
                result = "Erro ao exportar TXT: " + (error.message ?: "erro")
            }
        }
    }
    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(SavedLineFormat.encode(favorites).toByteArray(Charsets.UTF_8))
                } ?: error("Arquivo não criado.")
                result = "JSON exportado: " + favorites.size + " legendas."
            } catch (error: Exception) {
                result = "Erro ao exportar JSON: " + (error.message ?: "erro")
            }
        }
    }
    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Arquivo vazio.")
                val (list, count) = store.restore(text)
                favorites = list
                result = "Importadas " + count + " legendas novas."
            } catch (error: Exception) {
                result = "Erro ao importar: " + (error.message ?: "JSON inválido")
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Favoritar para estudar depois", fontWeight = FontWeight.SemiBold)
            Text(
                "A legenda e seus timestamps ficam salvos no aparelho, sem criar cartão.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF63727C)
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Filme ou série") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = season, onValueChange = { season = it },
                    label = { Text("Temporada") }, modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = episode, onValueChange = { episode = it },
                    label = { Text("Episódio") }, modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = activeCue != null,
                onClick = {
                    val cue = activeCue
                    if (cue != null) {
                        try {
                            val count = favorites.size
                            favorites = store.add(SavedLine(
                                title = title.trim().ifBlank { "Sem título" },
                                season = season.trim(), episode = episode.trim(),
                                text = cue.text,
                                startMs = (cue.startMs - offsetMs).coerceAtLeast(0),
                                endMs = (cue.endMs - offsetMs).coerceAtLeast(0),
                                subtitleStartMs = cue.startMs,
                                subtitleEndMs = cue.endMs
                            ))
                            result = if (favorites.size > count) "Legenda adicionada às favoritas."
                                else "Essa legenda já está salva."
                        } catch (error: Exception) {
                            result = "Falha ao salvar: " + (error.message ?: "erro")
                        }
                    }
                }
            ) { Text("★  Favoritar legenda atual") }
            Text(
                "Minhas favoritas (" + favorites.size + ")",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportTxt.launch("legendas-favoritas.txt") },
                    enabled = favorites.isNotEmpty()
                ) { Text("Exportar TXT") }
                OutlinedButton(
                    onClick = { exportJson.launch("legendas-favoritas.json") },
                    enabled = favorites.isNotEmpty()
                ) { Text("Exportar JSON") }
            }
            TextButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }) {
                Text("Importar backup JSON")
            }
            if (result.isNotBlank()) Text(
                result, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
            if (favorites.isEmpty()) Text(
                "Nenhuma legenda salva ainda.", style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF63727C)
            )
            favorites.asReversed().take(30).forEach { line ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F7))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            line.title +
                                (if (line.season.isBlank()) "" else " · T" + line.season) +
                                (if (line.episode.isBlank()) "" else " · E" + line.episode),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(SavedLineFormat.time(line.startMs) + " → " + SavedLineFormat.time(line.endMs),
                            color = MaterialTheme.colorScheme.primary)
                        Text(line.text)
                        TextButton(onClick = {
                            try {
                                favorites = store.remove(line.id)
                                result = "Legenda removida."
                            } catch (error: Exception) {
                                result = "Erro ao remover: " + error.message
                            }
                        }) { Text("Remover") }
                    }
                }
            }
            if (favorites.size > 30) Text(
                "Exibindo as 30 mais recentes; as exportações incluem todas.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
