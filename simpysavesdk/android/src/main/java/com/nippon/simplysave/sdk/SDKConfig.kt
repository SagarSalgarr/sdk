package com.nippon.simplysave.sdk

/**
 * Initialization configuration for the SDK.
 */
data class SDKConfig(
    val targetLanguage: Language = Language.HINDI,
    val ttsEnabled: Boolean = true,
    val ttsEnabledForTextMode: Boolean = false,
    val intentAcceptThreshold: Float = 0.85f,
    val intentRejectThreshold: Float = 0.45f,
    val maxClarificationTurns: Int = 3,
    val storageQuotaMB: Int = 450,
    val customVocabulary: List<String> = emptyList(),
    /** Base path for lightweight models: files at {modelLocalBasePath}/{logicalName}/{fileName}. If set, LOCAL_PATH models are picked from here. */
    val modelLocalBasePath: String? = null,
    /** Single base URL where all REMOTE_DOWNLOAD models are hosted. Required for heavy models; not a CDN — one URL for all. */
    val modelDownloadBaseUrl: String? = null,
    /** When true, recording runs until stop (no VAD); use for "press Stop when done". When false, VAD auto-stops after silence. */
    val useContinuousRecording: Boolean = false,
    /** When true, intent is not classified; use [hardcodedIntent] so STT/translation can be tested without an intent model. Set to false when intent classification model is added. */
    val useHardcodedIntent: Boolean = false,
    /** Intent to use when [useHardcodedIntent] is true. */
    val hardcodedIntent: Intent = Intent.CHECK_BALANCE
)
