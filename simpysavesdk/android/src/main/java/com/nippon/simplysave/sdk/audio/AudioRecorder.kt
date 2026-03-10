package com.nippon.simplysave.sdk.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.nippon.simplysave.sdk.config.Constants
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.concurrent.Volatile

/**
 * Records audio from microphone with optional VAD. When [recordUntilStopped] is true, records
 * continuously until stopAndGetWavBytes() (no VAD gating); use for "press Stop when done" flows.
 */
class AudioRecorder(
    private val context: Context,
    private val onAudioLevel: ((Float) -> Unit)? = null,
    private val recordUntilStopped: Boolean = false
) {
    @Volatile
    private var running = false
    @Volatile
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val buffer = ByteArrayOutputStream()
    private val vad = VoiceActivityDetector()
    private val sampleRate = Constants.SAMPLE_RATE_HZ
    private val frameSamples = sampleRate * Constants.VAD_FRAME_MS / 1000
    private val preBufferFrames = Constants.PRE_BUFFER_MS / Constants.VAD_FRAME_MS

    private val preBuffer = mutableListOf<ByteArray>()
    private var speechFrameCount = 0

    fun startRecording() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = minBuf * 4
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }
        buffer.reset()
        vad.reset()
        preBuffer.clear()
        speechFrameCount = 0
        running = true
        recordThread = Thread {
            audioRecord?.startRecording()
            val frameBuf = ShortArray(frameSamples)
            val byteBuf = ByteArray(frameSamples * 2)
            while (running && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(frameBuf, 0, frameSamples) ?: -1
                if (read <= 0) continue
                for (i in 0 until read) {
                    byteBuf[i * 2] = (frameBuf[i].toInt() and 0xff).toByte()
                    byteBuf[i * 2 + 1] = (frameBuf[i].toInt() shr 8 and 0xff).toByte()
                }
                val floatFrame = FloatArray(read) { frameBuf[it] / 32768f }
                onAudioLevel?.invoke(sqrt(floatFrame.map { it * it }.average().toFloat()))
                if (recordUntilStopped) {
                    buffer.write(byteBuf, 0, read * 2)
                } else {
                vad.processFrame(floatFrame)
                when (vad.state) {
                    VoiceActivityDetector.State.SILENCE -> {
                        if (preBuffer.size < preBufferFrames) {
                            preBuffer.add(byteBuf.copyOf(read * 2))
                        } else if (buffer.size() > 0) {
                            speechFrameCount++
                            if (speechFrameCount >= Constants.VAD_SILENCE_MS / Constants.VAD_FRAME_MS) {
                                running = false
                                break
                            }
                        }
                    }
                    VoiceActivityDetector.State.SPEECH -> {
                        speechFrameCount = 0
                        preBuffer.forEach { buffer.write(it) }
                        preBuffer.clear()
                        buffer.write(byteBuf, 0, read * 2)
                    }
                }
                }
            }
            audioRecord?.stop()
        }.apply { start() }
    }

    fun stopAndGetWavBytes(): ByteArray? {
        running = false
        recordThread?.join(2000)
        audioRecord?.release()
        audioRecord = null
        recordThread = null
        val pcm = buffer.toByteArray() ?: return null
        if (pcm.isEmpty()) return null
        return WavHeader.wrapPcm(pcm, sampleRate)
    }

    private fun sqrt(x: Float) = kotlin.math.sqrt(x)
}
