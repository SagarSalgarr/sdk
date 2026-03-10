package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.dialogue.DialogueStateTracker
import com.nippon.simplysave.sdk.dialogue.ResponseGenerator
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class DialogueManagementStep(
    private val dialogueTracker: DialogueStateTracker,
    private val sessionId: String,
    private val maxClarificationTurns: Int
) : PipelineStep {
    override fun shouldSkip(context: PipelineContext): Boolean = false

    override fun execute(context: PipelineContext) {
        val intent = context.intent ?: return
        val state = dialogueTracker.getOrCreate(sessionId)
        dialogueTracker.updateIntent(sessionId, intent)
        val turn = dialogueTracker.incrementTurn(sessionId)
        context.slotsCollected = state.slotsCollected

        val pending = context.slotsPending
        val allRequiredFilled = pending.isEmpty()

        context.responseEnglish = when {
            allRequiredFilled -> {
                state.pendingConfirmationIntent = null
                context.isComplete = true
                ResponseGenerator.getCompletionResponse(intent, context.slotsCollected)
            }
            context.requiresConfirmation -> {
                state.pendingConfirmationIntent = intent
                ResponseGenerator.getConfirmationResponse(intent, context.slotsCollected)
            }
            state.clarificationCount >= maxClarificationTurns -> {
                state.pendingConfirmationIntent = null
                context.errorCode = "MAX_CLARIFICATION_EXCEEDED"
                context.errorMessage = "Too many clarification turns"
                "Sorry, I couldn't gather the details. Please try again."
            }
            else -> {
                state.pendingConfirmationIntent = null
                dialogueTracker.incrementClarification(sessionId)
                val firstPending = pending.firstOrNull() ?: ""
                ResponseGenerator.getClarificationResponse(intent, firstPending, context.slotsCollected)
            }
        }
    }
}
