package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.config.Constants
import com.nippon.simplysave.sdk.inference.TTSRouter
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep
import java.io.File

class TextToSpeechStep(
    private val ttsEnabled: Boolean,
    private val ttsEnabledForTextMode: Boolean,
    private val cacheDir: File,
    private val sessionId: String,
    private val turn: Int,
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager
) : PipelineStep {

    override fun shouldSkip(context: PipelineContext): Boolean {
        if (!ttsEnabled) return true
        if (context.inputMode == PipelineContext.InputMode.TEXT && !ttsEnabledForTextMode) return true
        return false
    }

    override fun execute(context: PipelineContext) {
        val descriptor = registry.getModelFor(ModelType.TTS_END_TO_END, context.targetLanguage)
            ?: registry.getModelFor(ModelType.TTS_ACOUSTIC, context.targetLanguage)
        if (descriptor != null) {
            try {
                lifecycleManager.ensureLoaded(descriptor)
            } catch (_: Exception) { /* fallback: no TTS */ }
        }
        val outDir = File(cacheDir, Constants.TTS_OUTPUT_DIR)
        outDir.mkdirs()
        val outFile = File(outDir, "${sessionId}_$turn.wav")
        val success = TTSRouter.synthesize(
            context.responseTargetLanguage,
            context.targetLanguage,
            outFile.absolutePath
        )
        context.audioOutputPath = if (success) outFile.absolutePath else null
        context.audioSampleRate = 22050
    }
}
