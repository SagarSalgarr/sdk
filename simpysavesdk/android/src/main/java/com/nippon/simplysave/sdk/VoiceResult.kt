package com.nippon.simplysave.sdk

/**
 * Result of a single voice/text turn from the pipeline.
 */
data class VoiceResult(
    val sessionId: String,
    val turn: Int,
    val intent: Intent,
    val confidence: Float,
    val isComplete: Boolean,
    val slotsCollected: Map<String, Any>,
    val slotsPending: List<String>,
    val responseTextTarget: String,
    val responseTextEnglish: String,
    val audioFilePath: String?,
    val audioSampleRate: Int,
    val processingTimeMs: Long,
    val language: Language,
    val requiresConfirmation: Boolean = false
)
