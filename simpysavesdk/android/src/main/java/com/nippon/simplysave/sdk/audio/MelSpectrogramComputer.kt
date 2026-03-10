package com.nippon.simplysave.sdk.audio

import com.nippon.simplysave.sdk.config.Constants
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes 80-bin mel spectrogram from float32 PCM.
 */
object MelSpectrogramComputer {

    fun compute(
        samples: FloatArray,
        sampleRate: Int = Constants.SAMPLE_RATE_HZ,
        nMel: Int = Constants.MEL_FILTER_BANKS,
        hopLength: Int = Constants.MEL_HOP_LENGTH,
        winLength: Int = Constants.MEL_WINDOW_LENGTH,
        fftSize: Int = Constants.FFT_SIZE
    ): Array<FloatArray> {
        val nFrames = 1 + (samples.size - winLength) / hopLength
        if (nFrames <= 0) return arrayOf(FloatArray(nMel))
        val melBands = Array(nFrames) { FloatArray(nMel) }
        val window = hannWindow(winLength)
        val melFilter = melFilterbank(nMel, fftSize / 2 + 1, sampleRate)
        for (f in 0 until nFrames) {
            val start = f * hopLength
            val frame = FloatArray(fftSize) { i ->
                if (i < winLength && start + i < samples.size)
                    samples[start + i] * window[i]
                else 0f
            }
            val magnitude = fftMagnitude(frame, fftSize)
            for (m in 0 until nMel) {
                var sum = 0.0
                for (k in melFilter[m].indices) {
                    sum += magnitude[k] * melFilter[m][k]
                }
                melBands[f][m] = (if (sum > 1e-10) log2(sum + 1e-10f) else -10f).toFloat()
            }
        }
        return melBands
    }

    private fun hannWindow(length: Int): FloatArray =
        FloatArray(length) { i -> (0.5f * (1 - kotlin.math.cos(2 * Math.PI * i / (length - 1)))).toFloat() }

    private fun melFilterbank(
        nMel: Int,
        nFft: Int,
        sampleRate: Int
    ): Array<FloatArray> {
        val fftFreqs = FloatArray(nFft) { it * sampleRate.toFloat() / (nFft - 1) * 2 }
        val lowFreq = 0f
        val highFreq = sampleRate / 2f
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val melPoints = FloatArray(nMel + 2) { i ->
            melToHz(lowMel + (highMel - lowMel) * i / (nMel + 1))
        }
        val filterbank = Array(nMel) { FloatArray(nFft) }
        for (i in 0 until nMel) {
            val left = melPoints[i]
            val center = melPoints[i + 1]
            val right = melPoints[i + 2]
            for (j in 0 until nFft) {
                val freq = fftFreqs[j]
                when {
                    freq < left || freq > right -> filterbank[i][j] = 0f
                    freq < center -> filterbank[i][j] = (freq - left) / (center - left)
                    else -> filterbank[i][j] = (right - freq) / (right - center)
                }
            }
        }
        return filterbank
    }

    private fun hzToMel(hz: Float): Float =
        (2595 * log2(1 + hz / 700)).toFloat()

    private fun melToHz(mel: Float): Float =
        (700 * (2.0.pow((mel / 2595).toDouble()) - 1)).toFloat()

    private fun fftMagnitude(frame: FloatArray, fftSize: Int): FloatArray {
        val out = FloatArray(fftSize / 2 + 1)
        val n = frame.size
        for (k in out.indices) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val angle = -2 * Math.PI * k * t / n
                re += frame[t] * kotlin.math.cos(angle)
                im += frame[t] * kotlin.math.sin(angle)
            }
            out[k] = sqrt((re * re + im * im).toFloat())
        }
        return out
    }

    private fun log2(x: Float): Float = (kotlin.math.ln(x.toDouble()) / kotlin.math.ln(2.0)).toFloat()
}
