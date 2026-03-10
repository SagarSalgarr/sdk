package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.VoiceResult
import com.nippon.simplysave.sdk.Intent
import com.nippon.simplysave.sdk.pipeline.PipelineContext

object OutputAssemblyStep {
    fun buildResult(context: PipelineContext, sessionId: String, turn: Int): VoiceResult {
        return VoiceResult(
            sessionId = sessionId,
            turn = turn,
            intent = context.intent ?: Intent.FALLBACK,
            confidence = context.confidence,
            isComplete = context.isComplete,
            slotsCollected = context.slotsCollected.toMap(),
            slotsPending = context.slotsPending,
            responseTextTarget = context.responseTargetLanguage,
            responseTextEnglish = context.responseEnglish,
            audioFilePath = context.audioOutputPath,
            audioSampleRate = context.audioSampleRate,
            processingTimeMs = System.currentTimeMillis() - context.processingStartMs,
            language = context.targetLanguage,
            requiresConfirmation = context.requiresConfirmation
        )
    }
}
