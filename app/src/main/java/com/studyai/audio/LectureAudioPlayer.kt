package com.studyai.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioPlayerUiState(
    val isVisible: Boolean = false,
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeUrl: String? = null,
    val errorMessage: String? = null,
)

class LectureAudioPlayer @OptIn(UnstableApi::class) constructor(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val exoPlayer: ExoPlayer = run {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "ngrok-skip-browser-warning" to "true",
                    "User-Agent" to "StudyAiAndroid/1.0",
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val state: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(
                    isPlaying = isPlaying,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    errorMessage = if (isPlaying) null else _state.value.errorMessage,
                )
                if (isPlaying) startTicker() else tickerJob?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isReady = playbackState == Player.STATE_READY,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                tickerJob?.cancel()
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isReady = false,
                    errorMessage = "Không thể phát audio bài giảng (${error.errorCodeName}).",
                )
            }
        })
    }

    fun loadAndSeek(audioUrl: String, seekSec: Float) {
        val targetMs = (seekSec * 1000f).toLong().coerceAtLeast(0L)
        if (_state.value.activeUrl != audioUrl) {
            exoPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
            exoPlayer.prepare()
        }
        _state.value = _state.value.copy(
            isVisible = true,
            activeUrl = audioUrl,
            currentPositionMs = targetMs,
            errorMessage = null,
        )
        exoPlayer.seekTo(targetMs)
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekToMs(ms: Long) {
        val clamped = ms.coerceAtLeast(0L)
        exoPlayer.seekTo(clamped)
        _state.value = _state.value.copy(currentPositionMs = clamped)
    }

    fun cycleSpeed() {
        val next = when (_state.value.playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        exoPlayer.playbackParameters = PlaybackParameters(next)
        _state.value = _state.value.copy(playbackSpeed = next)
    }

    fun close() {
        tickerJob?.cancel()
        exoPlayer.pause()
        _state.value = _state.value.copy(isVisible = false, isPlaying = false)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                _state.value = _state.value.copy(
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                )
                delay(250)
            }
        }
    }

    fun release() {
        tickerJob?.cancel()
        exoPlayer.release()
    }
}
