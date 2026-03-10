package com.nippon.simplysave.sdk.pipeline

interface PipelineStep {
    fun shouldSkip(context: PipelineContext): Boolean
    fun execute(context: PipelineContext)
}
