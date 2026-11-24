package com.studyai.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioLectureRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    fun start(lecturePrefix: String = "lecture"): File {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "${lecturePrefix}_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        return file
    }

    fun getNormalizedAmplitude(): Float {
        return try {
            val maxAmp = recorder?.maxAmplitude ?: 0
            (maxAmp / 32767f).coerceIn(0.05f, 1.0f)
        } catch (_: Exception) {
            0.05f
        }
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }
}
