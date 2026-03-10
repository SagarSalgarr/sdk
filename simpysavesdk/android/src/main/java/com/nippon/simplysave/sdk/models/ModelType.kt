package com.nippon.simplysave.sdk.models

/**
 * Task type used by the pipeline to ask the registry for the right model.
 * No component other than the registry and lifecycle manager uses this.
 */
enum class ModelType {
    STT,
    TRANSLATION_TO_ENGLISH,
    TRANSLATION_FROM_ENGLISH,
    INTENT_CLASSIFICATION,
    TTS_ACOUSTIC,
    TTS_VOCODER,
    TTS_END_TO_END
}
