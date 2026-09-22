package com.example.eighten

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.eighten.ui.theme.EightenTheme
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? by mutableStateOf(null)
    private var playbackStatus by mutableStateOf("Stopped")
    private var playbackButtonState by mutableStateOf(PlaybackButtonState.PLAY)
    private var ignoresBatteryOptimizations by mutableStateOf(false)
    private var playWhenConnected = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackStatus()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackStatus()
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackStatus = "Playback error: ${error.errorCodeName}"
            playbackButtonState = PlaybackButtonState.PLAY
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startPlayback()
            } else {
                playbackStatus = "Notification permission is required for background playback"
                playbackButtonState = PlaybackButtonState.PLAY
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EightenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RadioControls(
                        status = playbackStatus,
                        playbackButtonState = playbackButtonState,
                        backgroundPlaybackAllowed = ignoresBatteryOptimizations,
                        onPlay = ::requestPlayback,
                        onStop = ::stopPlayback,
                        onAllowBackgroundPlayback = ::requestBatteryOptimizationExemption,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToPlaybackService()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptimizationStatus()
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(this, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener(
                    {
                        try {
                            controller = future.get().also {
                                it.addListener(playerListener)
                                updatePlaybackStatus()
                            }
                            if (playWhenConnected) {
                                startPlayback()
                            }
                        } catch (_: CancellationException) {
                            playbackStatus = "Player connection cancelled"
                            playbackButtonState = PlaybackButtonState.PLAY
                        } catch (error: ExecutionException) {
                            playbackStatus =
                                "Unable to connect: ${error.cause?.message ?: error.message}"
                            playbackButtonState = PlaybackButtonState.PLAY
                        } catch (error: InterruptedException) {
                            Thread.currentThread().interrupt()
                            playbackStatus = "Player connection interrupted"
                            playbackButtonState = PlaybackButtonState.PLAY
                        }
                    },
                    ContextCompat.getMainExecutor(this)
                )
            }
    }

    private fun requestPlayback() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startPlayback()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startPlayback() {
        playbackButtonState = PlaybackButtonState.LOADING
        playbackStatus = "Connecting..."

        val currentController = controller
        if (currentController == null) {
            playWhenConnected = true
        } else {
            playWhenConnected = false
            currentController.run {
                prepare()
                play()
            }
        }
    }

    private fun stopPlayback() {
        playWhenConnected = false
        controller?.stop()
        playbackStatus = "Stopped"
        playbackButtonState = PlaybackButtonState.PLAY
    }

    private fun updateBatteryOptimizationStatus() {
        val powerManager = getSystemService(PowerManager::class.java)
        ignoresBatteryOptimizations =
            powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )

        try {
            startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun updatePlaybackStatus() {
        val currentController = controller ?: return
        when {
            currentController.playerError != null -> {
                playbackStatus =
                    "Playback error: ${currentController.playerError?.errorCodeName}"
                playbackButtonState = PlaybackButtonState.PLAY
            }
            currentController.isPlaying -> {
                playbackStatus = "Playing live"
                playbackButtonState = PlaybackButtonState.STOP
            }
            currentController.playbackState == Player.STATE_BUFFERING -> {
                playbackStatus = "Connecting..."
                playbackButtonState = PlaybackButtonState.LOADING
            }
            currentController.playbackState == Player.STATE_ENDED -> {
                playbackStatus = "Stream ended"
                playbackButtonState = PlaybackButtonState.PLAY
            }
            else -> {
                playbackStatus = "Stopped"
                playbackButtonState = PlaybackButtonState.PLAY
            }
        }
    }
}

enum class PlaybackButtonState {
    PLAY,
    LOADING,
    STOP
}

@Composable
fun RadioControls(
    status: String,
    playbackButtonState: PlaybackButtonState,
    backgroundPlaybackAllowed: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onAllowBackgroundPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Sports Radio 810 WHB")
        Text(
            text = status,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(
            onClick = {
                when (playbackButtonState) {
                    PlaybackButtonState.PLAY -> onPlay()
                    PlaybackButtonState.STOP -> onStop()
                    PlaybackButtonState.LOADING -> Unit
                }
            },
            enabled = playbackButtonState != PlaybackButtonState.LOADING,
            modifier = Modifier.width(120.dp)
        ) {
            when (playbackButtonState) {
                PlaybackButtonState.PLAY -> Text("Play")
                PlaybackButtonState.STOP -> Text("Stop")
                PlaybackButtonState.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.dp
                )
            }
        }
        if (backgroundPlaybackAllowed) {
            Text(
                text = "Background playback allowed",
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            Text(
                text = "Allow unrestricted battery use to reduce background interruptions.",
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)
            )
            OutlinedButton(
                onClick = onAllowBackgroundPlayback,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Allow background playback")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RadioControlsPreview() {
    EightenTheme {
        RadioControls(
            status = "Stopped",
            playbackButtonState = PlaybackButtonState.PLAY,
            backgroundPlaybackAllowed = false,
            onPlay = {},
            onStop = {},
            onAllowBackgroundPlayback = {}
        )
    }
}
