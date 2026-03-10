package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.inference.IntentClassifierEngine
import com.nippon.simplysave.sdk.Intent
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

/**
 * Intent step: when [useHardcodedIntent] is true, sets [hardcodedIntent] (for testing STT/translation without an intent model).
 * Otherwise uses keyword/semantic classification (or later an intent classification model). Session-aware for confirmations.
 */
class IntentClassificationStep(
    private val registry: ModelRegistry,
    private val lifecycleManager: ModelLifecycleManager,
    private val dialogueTracker: com.nippon.simplysave.sdk.dialogue.DialogueStateTracker,
    private val sessionId: String,
    private val acceptThreshold: Float,
    private val rejectThreshold: Float,
    private val useHardcodedIntent: Boolean = false,
    private val hardcodedIntent: Intent = Intent.CHECK_BALANCE
) : PipelineStep {

    override fun shouldSkip(context: PipelineContext): Boolean = false

    override fun execute(context: PipelineContext) {
        if (useHardcodedIntent) {
            context.intent = hardcodedIntent
            context.confidence = 1f
            return
        }
        val state = dialogueTracker.getOrCreate(sessionId)
        val text = context.englishText.ifBlank { context.transcript }.trim().lowercase()
        if (state.pendingConfirmationIntent != null && isAffirmative(text)) {
            context.intent = state.pendingConfirmationIntent
            context.confidence = 1f
            state.pendingConfirmationIntent = null
            return
        }

        // Intent is keyword/semantic only — no model load
        val fullText = context.englishText.ifBlank { context.transcript }
        val (intent, confidence) = IntentClassifierEngine.classify(fullText)
        context.intent = when {
            confidence >= acceptThreshold -> intent
            confidence < rejectThreshold -> Intent.FALLBACK
            else -> intent.also { context.requiresConfirmation = true }
        }
        context.confidence = confidence
    }

    private fun isAffirmative(text: String): Boolean {
        val t = text.trim().lowercase()
        return t == "yes" || t == "yeah" || t == "yep" || t == "correct" || t == "haan" ||
            t == "हाँ" || t == "हां" || t == "सही" || t == "ठीक"
    }
}
