package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.inference.WhisperEngine
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

/**
 * Nurse: asks registry for STT model, tells lifecycle to ensure it's loaded, then hands to WhisperEngine.
 * Pipeline never calls ONNX directly.
 */
class SpeechToTextStep(
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager,
    private val customVocabulary: List<String>
) : PipelineStep {

    override fun shouldSkip(context: PipelineContext): Boolean =
        context.inputMode == PipelineContext.InputMode.TEXT

    override fun execute(context: PipelineContext) {
        val descriptor = registry.getModelFor(ModelType.STT, Language.ENGLISH) ?: run {
            context.transcript = ""
            return
        }
        try {
            lifecycleManager.ensureLoaded(descriptor)
        } catch (_: Exception) {
            context.errorCode = "MODEL_NOT_AVAILABLE"
            context.errorMessage = "STT model failed to load"
            return
        }
        val mel = context.melSpectrogram ?: return
        val engine = lifecycleManager.getEngineForType<WhisperEngine>(ModelType.STT)
        context.transcript = engine?.transcribe(mel, customVocabulary) ?: ""
    }
}
