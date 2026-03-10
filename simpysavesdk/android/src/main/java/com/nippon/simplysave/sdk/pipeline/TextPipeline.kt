package com.nippon.simplysave.sdk.pipeline

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.SDKCallback
import com.nippon.simplysave.sdk.dialogue.DialogueStateTracker
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.pipeline.steps.*
import com.nippon.simplysave.sdk.telemetry.TelemetryCollector

class TextPipeline(
    private val sessionId: String,
    private val targetLanguage: Language,
    private val config: com.nippon.simplysave.sdk.SDKConfig,
    private val cacheDir: java.io.File,
    private val dialogueTracker: DialogueStateTracker,
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager
) {
    private val steps = listOf(
        LanguageDetectionStep(),
        IndicToEnglishTranslationStep(registry, lifecycleManager),
        IntentClassificationStep(registry, lifecycleManager, dialogueTracker, sessionId, config.intentAcceptThreshold, config.intentRejectThreshold, config.useHardcodedIntent, config.hardcodedIntent),
        SlotExtractionStep(dialogueTracker, sessionId),
        DialogueManagementStep(dialogueTracker, sessionId, config.maxClarificationTurns),
        EnglishToTargetTranslationStep(registry, lifecycleManager),
        TextToSpeechStep(
            config.ttsEnabled,
            config.ttsEnabledForTextMode,
            cacheDir,
            sessionId,
            dialogueTracker.getOrCreate(sessionId).turn + 1,
            registry,
            lifecycleManager
        )
    )

    fun run(text: String, callback: SDKCallback) {
        val state = dialogueTracker.getOrCreate(sessionId)
        val context = PipelineContext(
            inputMode = PipelineContext.InputMode.TEXT,
            targetLanguage = targetLanguage,
            transcript = text,
            englishText = text,
            processingStartMs = System.currentTimeMillis()
        )
        for (step in steps) {
            if (context.errorCode != null) break
            if (!step.shouldSkip(context)) step.execute(context)
        }
        if (context.errorCode != null) {
            callback.onError(context.errorCode!!, context.errorMessage ?: "Pipeline error")
            return
        }
        TelemetryCollector.recordPipelineLatency(sessionId, System.currentTimeMillis() - context.processingStartMs)
        val turn = state.turn
        val result = OutputAssemblyStep.buildResult(context, sessionId, turn)
        if (result.isComplete) callback.onIntentCompleted(result)
        else callback.onPartialResult(result)
    }
}
