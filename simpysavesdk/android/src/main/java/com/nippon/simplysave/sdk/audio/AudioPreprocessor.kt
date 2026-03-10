package com.nippon.simplysave.sdk.audio

import com.nippon.simplysave.sdk.config.Constants

/**
 * Converts PCM bytes to float32 normalized [-1, 1] and optionally downmixes/resamples.
 */
object AudioPreprocessor {

    fun process(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        targetSampleRate: Int = Constants.SAMPLE_RATE_HZ
    ): FloatArray {
        var samples = pcmToFloatMono(pcmBytes, channelCount)
        if (sampleRate != targetSampleRate) {
            samples = resample(samples, sampleRate, targetSampleRate)
        }
        return samples
    }

    private fun pcmToFloatMono(pcm: ByteArray, channels: Int): FloatArray {
        val frameCount = pcm.size / (2 * channels)
        val out = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0f
            for (c in 0 until channels) {
                val idx = (i * channels + c) * 2
                val s = ((pcm[idx].toInt() and 0xff) or (pcm[idx + 1].toInt() shl 8)).toShort()
                sum += s / 32768f
            }
            out[i] = sum / channels
        }
        return out
    }

    private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples
        val ratio = toRate.toDouble() / fromRate
        val outLen = (samples.size * ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcIdx = i / ratio
            val i0 = srcIdx.toInt().coerceIn(0, samples.size - 1)
            val i1 = (i0 + 1).coerceAtMost(samples.size - 1)
            val frac = srcIdx - i0
            out[i] = samples[i0] * (1 - frac).toFloat() + samples[i1] * frac.toFloat()
        }
        return out
    }
}
