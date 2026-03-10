package com.nippon.simplysave.sdk.config

/**
 * Build-time or runtime constants. No hardcoded secrets here.
 */
object Constants {
    const val SAMPLE_RATE_HZ = 16000
    const val MEL_FILTER_BANKS = 80
    const val MEL_HOP_LENGTH = 160
    const val MEL_WINDOW_LENGTH = 400
    const val FFT_SIZE = 512
    const val VAD_FRAME_MS = 20
    const val VAD_SPEECH_THRESHOLD = 0.01f
    const val VAD_SILENCE_MS = 800
    const val PRE_BUFFER_MS = 200
    const val MAX_RAM_MB = 200
    const val TTS_OUTPUT_DIR = "sdk_tts_output"
    const val MODELS_DIR = "sdk_models"
    const val CACHE_DIR = "sdk_cache"
}
