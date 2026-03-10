package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class LanguageDetectionStep : PipelineStep {
    override fun shouldSkip(context: PipelineContext): Boolean = false

    override fun execute(context: PipelineContext) {
        val t = context.transcript
        if (t.isBlank()) {
            context.detectedLanguage = Language.ENGLISH
            context.sourceIsEnglish = true
            return
        }
        val hasDevanagari = t.any { it.code in 0x0900..0x097F }
        val hasBengali = t.any { it.code in 0x0980..0x09FF }
        val hasGujarati = t.any { it.code in 0x0A80..0x0AFF }
        val hasOdia = t.any { it.code in 0x0B00..0x0B7F }
        context.detectedLanguage = when {
            hasDevanagari -> Language.HINDI
            hasBengali -> Language.BENGALI
            hasGujarati -> Language.GUJARATI
            hasOdia -> Language.ODIA
            else -> Language.ENGLISH
        }
        context.sourceIsEnglish = context.detectedLanguage == Language.ENGLISH
    }
}
