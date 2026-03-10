package com.nippon.simplysave.sdk.pipeline

import com.nippon.simplysave.sdk.Language

/**
 * Mutable context passed through pipeline steps. Input mode set at start.
 */
data class PipelineContext(
    var inputMode: InputMode = InputMode.VOICE,
    var targetLanguage: Language = Language.HINDI,
    var wavBytes: ByteArray? = null,
    var needsDownmix: Boolean = false,
    var needsResample: Boolean = false,
    var sampleRate: Int = 16000,
    var channelCount: Int = 1,
    var melSpectrogram: Array<FloatArray>? = null,
    var transcript: String = "",
    var detectedLanguage: Language = Language.ENGLISH,
    var sourceIsEnglish: Boolean = true,
    var englishText: String = "",
    var intent: com.nippon.simplysave.sdk.Intent? = null,
    var confidence: Float = 0f,
    var requiresConfirmation: Boolean = false,
    var slotsCollected: MutableMap<String, Any> = mutableMapOf(),
    var slotsPending: List<String> = emptyList(),
    var isComplete: Boolean = false,
    var responseEnglish: String = "",
    var responseTargetLanguage: String = "",
    var audioOutputPath: String? = null,
    var audioSampleRate: Int = 22050,
    var processingStartMs: Long = 0L,
    var errorCode: String? = null,
    var errorMessage: String? = null
) {
    enum class InputMode { VOICE, TEXT }
}
