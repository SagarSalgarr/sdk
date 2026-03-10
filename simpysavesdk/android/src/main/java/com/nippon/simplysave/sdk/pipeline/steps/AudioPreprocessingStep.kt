package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.audio.AudioPreprocessor
import com.nippon.simplysave.sdk.audio.MelSpectrogramComputer
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class AudioPreprocessingStep : PipelineStep {
    override fun shouldSkip(context: PipelineContext): Boolean =
        context.inputMode == PipelineContext.InputMode.TEXT

    override fun execute(context: PipelineContext) {
        val bytes = context.wavBytes ?: return
        val headerSize = 44
        if (bytes.size <= headerSize) return
        val pcm = bytes.copyOfRange(headerSize, bytes.size)
        val samples = AudioPreprocessor.process(
            pcm,
            context.sampleRate,
            context.channelCount,
            16000
        )
        context.melSpectrogram = MelSpectrogramComputer.compute(samples, 16000)
    }
}
