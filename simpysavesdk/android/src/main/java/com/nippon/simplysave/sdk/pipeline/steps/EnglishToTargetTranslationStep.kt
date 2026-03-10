package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.inference.IndicTransEngine
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class EnglishToTargetTranslationStep(
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager
) : PipelineStep {

    override fun shouldSkip(context: PipelineContext): Boolean = false

    override fun execute(context: PipelineContext) {
        val descriptor = registry.getModelFor(ModelType.TRANSLATION_FROM_ENGLISH, context.targetLanguage) ?: run {
            context.responseTargetLanguage = context.responseEnglish
            return
        }
        try {
            lifecycleManager.ensureLoaded(descriptor)
        } catch (_: Exception) {
            context.responseTargetLanguage = context.responseEnglish
            return
        }
        val normalized = com.nippon.simplysave.sdk.config.TextNormalizer.normalizeForTranslation(context.responseEnglish, context.targetLanguage)
        context.responseTargetLanguage = IndicTransEngine.translate(
            normalized,
            Language.ENGLISH,
            context.targetLanguage
        )
    }
}
