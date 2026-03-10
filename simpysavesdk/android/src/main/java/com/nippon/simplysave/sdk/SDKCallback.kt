package com.nippon.simplysave.sdk

/**
 * Callback for async voice/text processing results.
 */
interface SDKCallback {
    fun onPartialResult(result: VoiceResult)
    fun onIntentCompleted(result: VoiceResult)
    fun onError(code: String, message: String)
}
