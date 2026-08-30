package com.nexauren.nexaaudio

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var selectedUri by mutableStateOf<Uri?>(null)
    private var selectedName by mutableStateOf("No audio selected")
    private var isPlaying by mutableStateOf(false)
    private var durationMs by mutableIntStateOf(0)
    private var positionMs by mutableIntStateOf(0)

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers do not offer persistable permissions.
        }

        mediaPlayer?.release()
        selectedUri = uri
        selectedName = queryDisplayName(uri) ?: "Selected audio"
        isPlaying = false
        durationMs = 0
        positionMs = 0

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setOnPreparedListener { player ->
                durationMs = player.duration
                positionMs = 0
                player.start()
                isPlaying = true
            }
            setOnCompletionListener { player ->
                player.seekTo(0)
                positionMs = 0
                isPlaying = false
            }
            setOnErrorListener { _, _, _ ->
                isPlaying = false
                true
            }
            prepareAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexaAudioApp(
                name = selectedName,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onChoose = { audioPicker.launch(arrayOf("audio/*")) },
                onToggle = {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    }
                },
                onSeek = { value ->
                    mediaPlayer?.seekTo(value.toInt())
                    positionMs = value.toInt()
                },
                onTick = {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) positionMs = player.currentPosition
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

@Composable
private fun NexaAudioApp(
    name: String,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onChoose: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onTick: () -> Unit
) {
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500)
            onTick()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Nexa Audio", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Audio Player by Nexauren",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(40.dp))
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Slider(
                value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                onValueChange = onSeek,
                valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Text("${formatTime(positionMs)} / ${formatTime(durationMs)}")
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onChoose) { Text("Choose audio") }
                Button(onClick = onToggle, enabled = durationMs > 0) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
