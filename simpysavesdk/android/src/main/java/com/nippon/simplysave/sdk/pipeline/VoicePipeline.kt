package com.nippon.simplysave.sdk.pipeline

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.SDKCallback
import com.nippon.simplysave.sdk.VoiceResult
import com.nippon.simplysave.sdk.dialogue.DialogueStateTracker
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.steps.*
import java.io.File

class VoicePipeline(
    private val sessionId: String,
    private val targetLanguage: Language,
    private val config: com.nippon.simplysave.sdk.SDKConfig,
    private val cacheDir: File,
    private val dialogueTracker: DialogueStateTracker,
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager
) {
    private val steps = listOf(
        AudioValidationStep(),
        AudioPreprocessingStep(),
        SpeechToTextStep(registry, lifecycleManager, config.customVocabulary),
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

    fun run(wavBytes: ByteArray, callback: SDKCallback) {
        val context = PipelineContext(
            inputMode = PipelineContext.InputMode.VOICE,
            targetLanguage = targetLanguage,
            wavBytes = wavBytes,
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
        val turn = dialogueTracker.getOrCreate(sessionId).turn
        val result = OutputAssemblyStep.buildResult(context, sessionId, turn)
        if (result.isComplete) callback.onIntentCompleted(result)
        else callback.onPartialResult(result)
    }
}
