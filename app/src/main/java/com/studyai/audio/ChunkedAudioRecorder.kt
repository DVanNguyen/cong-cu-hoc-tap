package com.studyai.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class ChunkedAudioRecorder(
    private val context: Context
) {
    companion object {
        private const val TAG = "ChunkedAudioRecorder"
        private const val CHUNK_DURATION_MS = 8000L
    }

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var currentFile: File? = null
    private var chunkIndex = 0
    private var totalElapsedSec = 0f

    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(
        scope: CoroutineScope,
        onChunkReady: (file: File, startSec: Float) -> Unit
    ) {
        if (isRecording) return
        isRecording = true
        chunkIndex = 0
        totalElapsedSec = 0f

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isRecording) {
                    val startSec = totalElapsedSec
                    val file = createTempChunkFile(chunkIndex++)
                    currentFile = file

                    val started = startRecorder(file)
                    if (!started) {
                        Log.e(TAG, "Không thể khởi động MediaRecorder")
                        break
                    }

                    delay(CHUNK_DURATION_MS)

                    stopCurrentRecorder()
                    totalElapsedSec += (CHUNK_DURATION_MS / 1000f)

                    if (file.exists() && file.length() > 1024) {
                        withContext(Dispatchers.Main) {
                            onChunkReady(file, startSec)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Recording job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi trong chu trình ghi âm chunk", e)
            } finally {
                stopCurrentRecorder()
                isRecording = false
            }
        }
    }

    fun stopRecording(onFinalChunkReady: ((file: File, startSec: Float) -> Unit)? = null) {
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        val lastFile = currentFile
        val lastStartSec = totalElapsedSec
        stopCurrentRecorder()

        if (lastFile != null && lastFile.exists() && lastFile.length() > 1024) {
            onFinalChunkReady?.invoke(lastFile, lastStartSec)
        }
        currentFile = null
    }

    private fun startRecorder(outputFile: File): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi prepare/start MediaRecorder", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    private fun stopCurrentRecorder() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                    // Xảy ra nếu stop quá sớm trước khi có frame audio
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi release MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }
    }

    private fun createTempChunkFile(index: Int): File {
        val dir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }
        return File(dir, "chunk_${System.currentTimeMillis()}_$index.m4a")
    }
}
