package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.dialogue.DialogueStateTracker
import com.nippon.simplysave.sdk.dialogue.SlotFiller
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class SlotExtractionStep(
    private val dialogueTracker: DialogueStateTracker,
    private val sessionId: String
) : PipelineStep {
    override fun shouldSkip(context: PipelineContext): Boolean = false

    override fun execute(context: PipelineContext) {
        val intent = context.intent ?: return
        val englishText = context.englishText.ifBlank { context.transcript }
        val state = dialogueTracker.getOrCreate(sessionId)
        val (filled, pending) = SlotFiller.fillSlots(intent, englishText, state.slotsCollected)
        context.slotsCollected.clear()
        context.slotsCollected.putAll(filled)
        context.slotsPending = pending
        state.slotsCollected.putAll(filled)
    }
}
