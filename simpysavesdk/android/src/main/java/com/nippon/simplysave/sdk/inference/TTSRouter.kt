package com.nippon.simplysave.sdk.inference

import com.nippon.simplysave.sdk.Language

/**
 * TTS router: uses per-language model from registry (e.g. MMS-TTS). Target language comes from
 * pipeline context (frontend/speaker). Model is loaded on demand via ModelLifecycleManager.
 * Stub: returns false until inference is wired; replace with MMS-TTS/VITS inference.
 */
object TTSRouter {

    fun synthesize(text: String, language: Language, outputPath: String): Boolean {
        return false
    }
}
