package com.nippon.simplysave.sdk.pipeline.steps

import com.nippon.simplysave.sdk.audio.WavHeader
import com.nippon.simplysave.sdk.audio.WavValidator
import com.nippon.simplysave.sdk.config.Constants
import com.nippon.simplysave.sdk.pipeline.PipelineContext
import com.nippon.simplysave.sdk.pipeline.PipelineStep

class AudioValidationStep : PipelineStep {
    override fun shouldSkip(context: PipelineContext): Boolean =
        context.inputMode == PipelineContext.InputMode.TEXT

    override fun execute(context: PipelineContext) {
        var bytes = context.wavBytes ?: run {
            context.errorCode = "INVALID_AUDIO_FORMAT"
            context.errorMessage = "No audio data"
            return
        }
        if (bytes.size < 44) {
            context.errorCode = "INVALID_AUDIO_FORMAT"
            context.errorMessage = "WAV header too short"
            return
        }
        // If bytes don't start with RIFF, treat as raw PCM from recorder and wrap with WAV header
        if (!WavHeader.hasRiffMagic(bytes)) {
            if (bytes.size >= 44 && bytes.size % 2 == 0) {
                bytes = WavHeader.wrapPcm(bytes, Constants.SAMPLE_RATE_HZ)
                context.wavBytes = bytes
            } else {
                context.errorCode = "INVALID_AUDIO_FORMAT"
                context.errorMessage = "Invalid RIFF magic"
                return
            }
        }
        val result = WavValidator.validate(bytes.copyOfRange(0, 44))
        if (!result.valid) {
            context.errorCode = "INVALID_AUDIO_FORMAT"
            context.errorMessage = result.errorMessage ?: "Invalid WAV"
            return
        }
        context.sampleRate = result.sampleRate
        context.channelCount = result.channelCount
        context.needsDownmix = result.channelCount > 1
        context.needsResample = result.sampleRate != 16000
    }
}
