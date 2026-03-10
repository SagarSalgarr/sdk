package com.nippon.simplysave.sdk.config

import android.util.Log

/**
 * SDK logging. ProGuard strips Log.* in release; no user content (transcript, intents) in log messages.
 */
object SdkLogger {

    private const val TAG = "SimplySaveSDK"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, message, t) else Log.e(TAG, message)
    }
}
