package com.nippon.simplysave.sdk.audio

import java.io.ByteArrayOutputStream

/**
 * Validates WAV header and structure. Does not touch raw audio bytes beyond header.
 */
object WavValidator {

    private const val RIFF = 0x52494646
    private const val WAVE = 0x57415645
    private const val FMT = 0x666d7420
    private const val PCM_FORMAT = 1

    data class ValidationResult(
        val valid: Boolean,
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val dataSizeBytes: Int,
        val errorMessage: String?
    )

    fun validate(headerBytes: ByteArray): ValidationResult {
        if (headerBytes.size < 44) {
            return ValidationResult(false, 0, 0, 0, 0, "WAV header too short")
        }
        val riff = readInt(headerBytes, 0)
        if (riff != RIFF) {
            return ValidationResult(false, 0, 0, 0, 0, "Invalid RIFF magic")
        }
        val wave = readInt(headerBytes, 8)
        if (wave != WAVE) {
            return ValidationResult(false, 0, 0, 0, 0, "Invalid WAVE marker")
        }
        val fmt = readInt(headerBytes, 12)
        if (fmt != FMT) {
            return ValidationResult(false, 0, 0, 0, 0, "Invalid format chunk")
        }
        val formatCode = readShort(headerBytes, 20).toInt() and 0xFFFF
        if (formatCode != PCM_FORMAT) {
            return ValidationResult(false, 0, 0, 0, 0, "Only PCM format (1) supported")
        }
        val channelCount = readShort(headerBytes, 22).toInt() and 0xFFFF
        val sampleRate = readInt(headerBytes, 24)
        if (sampleRate !in 8000..48000) {
            return ValidationResult(false, 0, 0, 0, 0, "Sample rate out of range")
        }
        val bitsPerSample = readShort(headerBytes, 34).toInt() and 0xFFFF
        if (bitsPerSample != 16) {
            return ValidationResult(false, 0, 0, 0, 0, "Only 16-bit PCM supported")
        }
        var dataSize = 0
        var offset = 36
        while (offset + 8 <= headerBytes.size) {
            val chunkId = readInt(headerBytes, offset)
            val chunkSize = readInt(headerBytes, offset + 4)
            if (chunkId == 0x64617461) { // "data"
                dataSize = chunkSize
                break
            }
            offset += 8 + chunkSize
        }
        return ValidationResult(
            valid = true,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            dataSizeBytes = dataSize,
            errorMessage = null
        )
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun readShort(b: ByteArray, off: Int): Short =
        ((b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)).toShort()
}
