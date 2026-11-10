package com.oyayasu.ankivideoplayer

import android.content.Intent
import android.net.Uri
 import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Intent?>(null)
    private var externalPlayer: ExoPlayer? = null

    fun reportPlayback(player: ExoPlayer) { externalPlayer = player }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = intent.takeIf { it.action == Intent.ACTION_VIEW }
    }

    override fun finish() {
        externalPlayer?.let { player ->
            setResult(RESULT_OK, Intent().apply {
                putExtra("position", player.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                putExtra("duration", player.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            })
        }
        super.finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = intent.takeIf { it.action == Intent.ACTION_VIEW }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF12677A),
                    surface = Color.White,
                    background = Color(0xFFF5F7FA)
                )
            ) {
                VideoLearningScreen(incoming)
            }
        }
    }
}

private data class SubtitleChoice(
    val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoLearningScreen(incoming: Intent?) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val player = remember { ExoPlayer.Builder(context).build() }

    var videoUrl by remember { mutableStateOf("") }
    var currentMedia by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Abra um vídeo local ou cole um link direto.") }
    var mediaTitle by remember { mutableStateOf("") }
    var season by remember { mutableStateOf("") }
    var episode by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var embeddedCue by remember { mutableStateOf<CueLine?>(null) }
    var useExternalCues by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<SubtitleChoice>>(emptyList()) }
    val favoritesStore = remember(context) { SavedLineStore(context) }
    var favorites by remember { mutableStateOf(favoritesStore.load()) }
    var favoriteStatus by remember { mutableStateOf("") }
    var cues by remember { mutableStateOf<List<CueLine>>(emptyList()) }
    var position by remember { mutableLongStateOf(0L) }
    var offsetMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    var selectedWord by remember { mutableStateOf("") }
    var selectedSentence by remember { mutableStateOf("") }
    var showDictionary by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<WordEntry?>(null) }
    var jsonInput by remember { mutableStateOf("") }
    var dictionaryError by remember { mutableStateOf("") }

    fun saveFavorite(line: CueLine) {
        try {
            val before = favorites.size
            favorites = favoritesStore.add(SavedLine(
                title = mediaTitle.trim().ifBlank { "Sem título" },
                season = season.trim(),
                episode = episode.trim(),
                text = line.text,
                startMs = (line.startMs - if (useExternalCues) offsetMs else 0L).coerceAtLeast(0),
                endMs = (line.endMs - if (useExternalCues) offsetMs else 0L).coerceAtLeast(0),
                subtitleStartMs = line.startMs,
                subtitleEndMs = line.endMs
            ))
            favoriteStatus = if (favorites.size > before) "Legenda salva."
                else "Essa legenda já está nas favoritas."
        } catch (e: Exception) { favoriteStatus = "Erro ao salvar: " + e.message }
    }

    fun selectWord(token: String, line: CueLine) {
        val normalized = token.trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }
        if (normalized.isNotBlank()) {
            selectedWord = normalized
            selectedSentence = line.text
            entry = null
            jsonInput = ""
            dictionaryError = ""
            player.pause()
            showDictionary = true
        }
    }

    fun play(uri: Uri, titleHint: String? = null, startMs: Long = 0, mimeHint: String? = null) {
        cues = emptyList()
        useExternalCues = false
        embeddedCue = null
        choices = emptyList()
        currentMedia = (uri.lastPathSegment ?: "Vídeo").substringAfterLast("/").substringBeforeLast(".").take(120)
        mediaTitle = titleHint?.takeIf { it.isNotBlank() } ?: currentMedia
        season = ""
        episode = ""
        position = 0L
        offsetMs = 0L
        player.setMediaItem(ExternalPlayback.asMediaItem(uri, mimeHint))
        player.prepare()
        if (startMs > 0) player.seekTo(startMs)
        player.play()
        status = "Reproduzindo: " + (uri.lastPathSegment ?: uri.toString()).take(90)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            play(uri)
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                cues = SubtitleParser.parse(text)
                useExternalCues = cues.isNotEmpty()
                status = if (cues.isEmpty()) "Nenhuma legenda reconhecida." else "Legendas carregadas: " + cues.size
            } catch (error: Exception) {
                status = "Erro ao abrir legenda: " + (error.message ?: "desconhecido")
            }
        }
    }

    LaunchedEffect(incoming) {
        ExternalPlayback.parse(incoming)?.let { stream ->
            videoUrl = stream.uri.toString()
            play(stream.uri, stream.title, stream.positionMs, stream.mimeHint)
            fullscreen = true
        }
    }
    DisposableEffect(player) {
        activity.reportPlayback(player)
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                val text = cueGroup.cues.mapNotNull { it.text?.toString() }
                    .filter { it.isNotBlank() }.joinToString(" ").trim()
                if (text.isEmpty()) embeddedCue = null
                else {
                    val now = player.currentPosition.coerceAtLeast(0)
                    val old = embeddedCue
                    embeddedCue = if (old?.text == text) old else CueLine(now, now + 2500, text)
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                choices = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    .flatMap { group ->
                        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                            val format = group.getTrackFormat(index)
                            SubtitleChoice(
                                group, index,
                                format.label?.takeIf { it.isNotBlank() }
                                    ?: format.language?.takeIf { it.isNotBlank() }
                                    ?: ("Faixa " + (index + 1)),
                                group.isTrackSelected(index)
                            )
                        }
                    }
                if (choices.isNotEmpty() && choices.none { it.selected }) {
                    val first = choices.first()
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(first.group.mediaTrackGroup, listOf(first.index)))
                        .build()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                status = "Falha de reprodução: " + (error.message ?: "formato ou endereço não suportado")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            delay(150)
        }
    }

    val cue = if (useExternalCues) {
        cues.lastOrNull { position + offsetMs >= it.startMs && position + offsetMs < it.endMs }
    } else embeddedCue
    fun chooseTextTrack(choice: SubtitleChoice) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index)))
            .build()
        useExternalCues = false
    }

    if (fullscreen) {
        PlaybackOverlay(
            player = player, title = mediaTitle, position = position,
            caption = cue, fullscreen = true, modifier = Modifier.fillMaxSize(),
            onFullscreen = { fullscreen = it },
            onFavorite = { saveFavorite(it) },
            onWord = { token, line -> selectWord(token, line) }
        )
    } else {

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Anki Video Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Favoritar agora, estudar depois. Criar cartões é opcional.", color = Color(0xFF63727C))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { videoPicker.launch(arrayOf("video/*", "application/octet-stream", "*/*")) }) {
                            Text("Vídeo local")
                        }
                        OutlinedButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                            Text("Legenda")
                        }
                    }
                    OutlinedTextField(
                        value = videoUrl, onValueChange = { videoUrl = it },
                        label = { Text("URL direta: MP4, HLS, DASH…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(onClick = {
                        val uri = Uri.parse(videoUrl.trim())
                        if (uri.scheme == "https" || uri.scheme == "http") play(uri)
                        else status = "Informe uma URL http(s) de mídia."
                    }, enabled = videoUrl.isNotBlank()) { Text("Reproduzir URL") }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF586A75))
                }
            }

            PlaybackOverlay(
                player = player, title = mediaTitle, position = position,
                caption = cue, fullscreen = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                onFullscreen = { fullscreen = it },
                onFavorite = { saveFavorite(it) },
                onWord = { token, line -> selectWord(token, line) }
            )
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Legenda interativa", fontWeight = FontWeight.SemiBold)
                    if (choices.isNotEmpty()) {
                        Text("Legendas embutidas (selecione um idioma):",
                            style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            choices.forEach { option ->
                                OutlinedButton(onClick = { chooseTextTrack(option) }) {
                                    Text((if (option.selected && !useExternalCues) "✓ " else "") + option.label)
                                }
                            }
                        }
                    }
                    if (cues.isNotEmpty() && choices.isNotEmpty()) {
                        Row {
                            TextButton(onClick = { useExternalCues = false }) { Text("Interna") }
                            TextButton(onClick = { useExternalCues = true }) { Text("SRT externo") }
                        }
                    }
                    if (cue == null) {
                        Text(
                            if (choices.isEmpty() && cues.isEmpty())
                                "Nenhuma faixa de texto reconhecida. Abra SRT externo se necessário."
                            else "Nenhuma fala neste momento.",
                            color = Color(0xFF6E7A83)
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            cue.text.split(Regex("""\s+""")).filter { it.isNotEmpty() }.forEach { token ->
                                Text(
                                    text = token,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .background(Color(0xFFE9F4F6), RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectWord(token, cue)
                                        }.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val previous = cues.lastOrNull { it.startMs < position + offsetMs - 300 }
                            if (previous != null) player.seekTo((previous.startMs - offsetMs).coerceAtLeast(0))
                        }, enabled = cues.isNotEmpty()) { Text("Anterior") }
                        OutlinedButton(onClick = {
                            val current = cues.lastOrNull { it.startMs <= position + offsetMs }
                            if (current != null) player.seekTo((current.startMs - offsetMs).coerceAtLeast(0))
                            player.play()
                        }, enabled = cues.isNotEmpty()) { Text("Repetir") }
                        OutlinedButton(onClick = {
                            val next = cues.firstOrNull { it.startMs > position + offsetMs + 300 }
                            if (next != null) player.seekTo((next.startMs - offsetMs).coerceAtLeast(0))
                        }, enabled = cues.isNotEmpty()) { Text("Próxima") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Velocidade", style = MaterialTheme.typography.bodySmall)
                        listOf(0.75f, 1f, 1.25f).forEach { rate ->
                            TextButton(onClick = {
                                speed = rate
                                player.playbackParameters = PlaybackParameters(rate)
                            }) {
                                Text(rate.toString() + "×", fontWeight = if (speed == rate) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sincronia: " + (offsetMs / 1000f).toString() + "s", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { offsetMs -= 250 }) { Text("−0,25") }
                        TextButton(onClick = { offsetMs += 250 }) { Text("+0,25") }
                    }
                }
            }
            FavoriteSection(
                activeCue = cue, title = mediaTitle, onTitle = { mediaTitle = it },
                season = season, onSeason = { season = it },
                episode = episode, onEpisode = { episode = it },
                favorites = favorites, saveFavorite = { saveFavorite(it) },
                removeFavorite = { id ->
                    try { favorites = favoritesStore.remove(id); favoriteStatus = "Legenda removida." }
                    catch (e: Exception) { favoriteStatus = "Erro ao remover: " + e.message }
                },
                importFavorites = { json ->
                    try {
                        val (list, count) = favoritesStore.restore(json)
                        favorites = list
                        favoriteStatus = count.toString() + " novas legendas importadas."
                    } catch (e: Exception) { favoriteStatus = "Erro ao importar: " + e.message }
                },
                status = favoriteStatus
            )
        }
    }

    }

    if (showDictionary) {
        ModalBottomSheet(onDismissRequest = { showDictionary = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(selectedWord, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(selectedSentence, style = MaterialTheme.typography.bodyMedium)
                Text("Modo manual: copie o prompt e cole a resposta JSON da IA que preferir.")
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                        "ASBPlayer dictionary prompt", DictionaryClient.prompt(selectedWord, selectedSentence)
                    ))
                    dictionaryError = "Prompt copiado."
                }) { Text("Copiar prompt (modo manual)") }
                OutlinedTextField(
                    value = jsonInput, onValueChange = { jsonInput = it },
                    label = { Text("Cole o JSON manual aqui") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                OutlinedButton(onClick = {
                    try {
                        entry = DictionaryClient.decode(jsonInput, selectedWord, selectedSentence)
                        dictionaryError = ""
                    } catch (error: Exception) {
                        dictionaryError = error.message ?: "JSON inválido."
                    }
                }, enabled = jsonInput.isNotBlank()) { Text("Importar JSON") }
                if (dictionaryError.isNotBlank()) {
                    Text(dictionaryError, color = MaterialTheme.colorScheme.primary)
                }
                entry?.let { result ->
                    result.meanings.forEachIndexed { index, meaning ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F7))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text((index + 1).toString() + ". " + meaning.definition)
                                Text(meaning.partOfSpeech + "   " + meaning.ipa, color = Color(0xFF627786))
                            }
                        }
                    }
                    Button(onClick = {
                        try {
                            AnkiBridge.send(context, result)
                        } catch (error: Exception) {
                            dictionaryError = "Não foi possível abrir o compartilhamento: " + error.message
                        }
                    }) { Text("Compartilhar com AnkiDroid (opcional)") }
                }
            }
        }
    }
}
