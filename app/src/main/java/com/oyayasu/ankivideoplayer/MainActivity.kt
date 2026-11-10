package com.oyayasu.ankivideoplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF12677A),
                    surface = Color.White,
                    background = Color(0xFFF5F7FA)
                )
            ) {
                VideoLearningScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoLearningScreen() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    var videoUrl by remember { mutableStateOf("") }
    var currentMedia by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Abra um vídeo local ou cole um link direto.") }
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

    fun play(uri: Uri) {
        cues = emptyList()
        currentMedia = (uri.lastPathSegment ?: "Vídeo").substringAfterLast("/").substringBeforeLast(".").take(120)
        position = 0L
        offsetMs = 0L
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
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
                status = if (cues.isEmpty()) "Nenhuma legenda reconhecida." else "Legendas carregadas: " + cues.size
            } catch (error: Exception) {
                status = "Erro ao abrir legenda: " + (error.message ?: "desconhecido")
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
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

    val cue = cues.lastOrNull { position + offsetMs >= it.startMs && position + offsetMs < it.endMs }

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

            Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 2.dp, color = Color.Black) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            keepScreenOn = true
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Legenda interativa", fontWeight = FontWeight.SemiBold)
                    if (cue == null) {
                        Text(
                            if (cues.isEmpty()) "Carregue um arquivo SRT, VTT ou ASS para tocar nas palavras."
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
                                            val normalized = token.trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }
                                            if (normalized.isNotBlank()) {
                                                selectedWord = normalized
                                                selectedSentence = cue.text
                                                entry = null
                                                jsonInput = ""
                                                dictionaryError = ""
                                                player.pause()
                                                showDictionary = true
                                            }
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
                activeCue = cue,
                offsetMs = offsetMs,
                currentMedia = currentMedia
            )
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
