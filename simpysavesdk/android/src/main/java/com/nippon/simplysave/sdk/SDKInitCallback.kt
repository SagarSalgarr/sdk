package com.nippon.simplysave.sdk

/**
 * Callback for SDK initialization completion.
 * @param readyModelNames Logical names of models that were successfully loaded/downloaded (e.g. whisper_tiny_v1, indictrans2_en_indic_v1).
 */
interface SDKInitCallback {
    fun onReady(languages: List<Language>, readyModelNames: List<String>)
    fun onError(code: String, message: String)
}
