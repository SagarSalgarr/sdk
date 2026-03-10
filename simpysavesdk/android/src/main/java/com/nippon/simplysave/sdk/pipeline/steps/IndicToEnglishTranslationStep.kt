package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.inference.IndicTransEngine
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class IndicToEnglishTranslationStep(
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager
) : PipelineStep {

    override fun shouldSkip(context: PipelineContext): Boolean =
        context.sourceIsEnglish

    override fun execute(context: PipelineContext) {
        val descriptor = registry.getModelFor(ModelType.TRANSLATION_TO_ENGLISH, context.detectedLanguage) ?: run {
            context.englishText = context.transcript
            return
        }
        try {
            lifecycleManager.ensureLoaded(descriptor)
        } catch (_: Exception) {
            context.englishText = context.transcript
            return
        }
        context.englishText = IndicTransEngine.translate(
            context.transcript,
            context.detectedLanguage,
            Language.ENGLISH
        )
    }
}
