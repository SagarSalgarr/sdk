package com.nippon.simplysave.sdk.audio

import com.nippon.simplysave.sdk.config.Constants

/**
 * Voice activity detection: SILENCE <-> SPEECH based on RMS threshold.
 */
class VoiceActivityDetector(
    private val speechThreshold: Float = Constants.VAD_SPEECH_THRESHOLD,
    private val silenceMs: Int = Constants.VAD_SILENCE_MS,
    private val frameMs: Int = Constants.VAD_FRAME_MS
) {
    enum class State { SILENCE, SPEECH }

    var state: State = State.SILENCE
        private set
    private var silenceFrameCount = 0
    private val framesForSilence = silenceMs / frameMs

    fun processFrame(samples: FloatArray): State {
        val rms = sqrt(samples.map { it * it }.average().toFloat())
        return when (state) {
            State.SILENCE -> {
                if (rms >= speechThreshold) {
                    state = State.SPEECH
                    silenceFrameCount = 0
                }
                state
            }
            State.SPEECH -> {
                if (rms < speechThreshold) {
                    silenceFrameCount++
                    if (silenceFrameCount >= framesForSilence) {
                        state = State.SILENCE
                    }
                } else {
                    silenceFrameCount = 0
                }
                state
            }
        }
    }

    fun reset() {
        state = State.SILENCE
        silenceFrameCount = 0
    }

    private fun sqrt(x: Float) = kotlin.math.sqrt(x)
}
