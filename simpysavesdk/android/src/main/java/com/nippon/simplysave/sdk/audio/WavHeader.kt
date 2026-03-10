package com.nippon.simplysave.sdk.audio

import com.nippon.simplysave.sdk.config.Constants

/**
 * Builds standard 44-byte WAV header for 16-bit mono PCM.
 * Used by AudioRecorder and as fallback when pipeline receives raw PCM without header.
 */
object WavHeader {
    private const val RIFF_0 = 0x52
    private const val RIFF_1 = 0x49
    private const val RIFF_2 = 0x46
    private const val RIFF_3 = 0x46

    /**
     * Build 44-byte WAV header. PCM must be 16-bit mono; sampleRate typically 16000.
     */
    fun build(dataSize: Int, sampleRate: Int = Constants.SAMPLE_RATE_HZ, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val header = ByteArray(44)
        header[0] = RIFF_0.toByte(); header[1] = RIFF_1.toByte(); header[2] = RIFF_2.toByte(); header[3] = RIFF_3.toByte()
        val fileSize = dataSize + 36
        header[4] = (fileSize and 0xff).toByte(); header[5] = (fileSize shr 8 and 0xff).toByte()
        header[6] = (fileSize shr 16 and 0xff).toByte(); header[7] = (fileSize shr 24 and 0xff).toByte()
        header[8] = 0x57.toByte(); header[9] = 0x41.toByte(); header[10] = 0x56.toByte(); header[11] = 0x45.toByte()   // "WAVE"
        header[12] = 0x66.toByte(); header[13] = 0x6d.toByte(); header[14] = 0x74.toByte(); header[15] = 0x20.toByte()  // "fmt "
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = (channels and 0xff).toByte(); header[23] = (channels shr 8).toByte()
        header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = (bitsPerSample and 0xff).toByte(); header[35] = (bitsPerSample shr 8).toByte()
        header[36] = 0x64.toByte(); header[37] = 0x61.toByte(); header[38] = 0x74.toByte(); header[39] = 0x61.toByte()  // "data"
        header[40] = (dataSize and 0xff).toByte(); header[41] = (dataSize shr 8 and 0xff).toByte()
        header[42] = (dataSize shr 16 and 0xff).toByte(); header[43] = (dataSize shr 24 and 0xff).toByte()
        return header
    }

    /** Returns true if the first 4 bytes are "RIFF" (0x52,0x49,0x46,0x46). */
    fun hasRiffMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == RIFF_0.toByte() && bytes[1] == RIFF_1.toByte() &&
            bytes[2] == RIFF_2.toByte() && bytes[3] == RIFF_3.toByte()
    }

    /** Wrap raw PCM (16-bit mono) with a WAV header and return full WAV bytes. */
    fun wrapPcm(rawPcm: ByteArray, sampleRate: Int = Constants.SAMPLE_RATE_HZ): ByteArray =
        build(rawPcm.size, sampleRate, 1, 16) + rawPcm
}
