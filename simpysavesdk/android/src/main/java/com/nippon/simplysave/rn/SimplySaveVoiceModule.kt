package com.nippon.simplysave.rn

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.SDKInitCallback
import com.nippon.simplysave.sdk.SimplySaveSDK
import com.nippon.simplysave.sdk.SDKConfig
import com.nippon.simplysave.sdk.SDKCallback
import com.nippon.simplysave.sdk.VoiceResult
import com.nippon.simplysave.sdk.Intent
import java.util.concurrent.ConcurrentHashMap
import android.util.Log

/**
 * React Native Native Module bridge. Translates JS calls to the native AAR.
 * No business logic — only type conversion and Promise handling.
 */
class SimplySaveVoiceModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val sdk: SimplySaveSDK = SimplySaveSDK.getInstance()
    private val pendingPromises = ConcurrentHashMap<String, Promise>()
    private @Volatile var lastRecordingSessionId: String? = null

    override fun getName(): String = "SimplySaveVoiceModule"

    @ReactMethod
    fun initialize(licenseKey: String, configMap: ReadableMap?, promise: Promise) {
        runOnExecutor {
            try {
                val config = mapToSDKConfig(configMap)
                Log.d("SimplySaveVoice", "initialize: modelDownloadBaseUrl=${config.modelDownloadBaseUrl ?: "null"}")
                val progressCallback: (String) -> Unit = { msg ->
                    mainHandler.post { emitInitProgress(msg) }
                }
                sdk.initialize(reactApplicationContext, licenseKey, config, progressCallback, object : SDKInitCallback {
                    override fun onReady(languages: List<Language>, readyModelNames: List<String>) {
                        emitSDKReady(languages, readyModelNames)
                        val result = Arguments.createMap().apply {
                            putArray("readyModelNames", Arguments.createArray().apply {
                                readyModelNames.forEach { pushString(it) }
                            })
                        }
                        promise.resolve(result)
                    }
                    override fun onError(code: String, message: String) {
                        promise.reject(code, message)
                    }
                })
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun processVoice(audioFilePath: String, sessionId: String, languageString: String, promise: Promise) {
        pendingPromises[sessionId] = promise
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                sdk.processVoice(
                    android.net.Uri.parse(audioFilePath),
                    sessionId,
                    language,
                    callbackForSession(sessionId)
                )
            } catch (e: Exception) {
                pendingPromises.remove(sessionId)?.reject(e)
            }
        }
    }

    @ReactMethod
    fun processText(text: String, sessionId: String, languageString: String, promise: Promise) {
        pendingPromises[sessionId] = promise
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                sdk.processText(text, sessionId, language, callbackForSession(sessionId))
            } catch (e: Exception) {
                pendingPromises.remove(sessionId)?.reject(e)
            }
        }
    }

    @ReactMethod
    fun prepareLanguage(languageString: String, promise: Promise) {
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                sdk.prepareLanguage(reactApplicationContext, language) {
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun setLanguage(languageString: String, promise: Promise) {
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                sdk.setLanguage(language)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun isLanguageAvailable(languageString: String, promise: Promise) {
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                promise.resolve(sdk.isLanguageAvailable(language))
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun getSupportedLanguages(promise: Promise) {
        runOnExecutor {
            try {
                val list = sdk.getSupportedLanguages().map { it.name }
                promise.resolve(Arguments.createArray().apply {
                    list.forEach { pushString(it) }
                })
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun getModelStorageInfo(promise: Promise) {
        runOnExecutor {
            try {
                val info = sdk.getModelStorageInfo()
                if (info == null) {
                    promise.resolve(null)
                    return@runOnExecutor
                }
                val map = Arguments.createMap().apply {
                    putString("storagePath", info.storagePath)
                    putDouble("totalSizeBytes", info.totalSizeBytes.toDouble())
                    putArray("models", Arguments.createArray().apply {
                        info.models.forEach { m ->
                            pushMap(Arguments.createMap().apply {
                                putString("logicalName", m.logicalName)
                                putDouble("sizeBytes", m.sizeBytes.toDouble())
                                putInt("fileCount", m.fileCount)
                            })
                        }
                    })
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun startRecording(sessionId: String, promise: Promise) {
        lastRecordingSessionId = sessionId
        runOnExecutor {
            try {
                sdk.startRecording(sessionId) { rms ->
                    emitAudioLevel(rms)
                }
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun stopRecordingAndProcess(languageString: String, promise: Promise) {
        val sessionId = lastRecordingSessionId ?: "recording-session"
        pendingPromises[sessionId] = promise
        runOnExecutor {
            try {
                val language = Language.valueOf(languageString)
                sdk.stopRecordingAndProcess(language, callbackForSession(sessionId))
            } catch (e: Exception) {
                pendingPromises.remove(sessionId)?.reject(e)
            }
        }
    }

    @ReactMethod
    fun destroy(promise: Promise) {
        runOnExecutor {
            try {
                sdk.destroy()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }

    private fun callbackForSession(sessionId: String): SDKCallback =
        object : SDKCallback {
            override fun onPartialResult(result: VoiceResult) {
                mainHandler.post {
                    pendingPromises.remove(result.sessionId)?.resolve(voiceResultToMap(result))
                }
            }
            override fun onIntentCompleted(result: VoiceResult) {
                mainHandler.post {
                    pendingPromises.remove(result.sessionId)?.resolve(voiceResultToMap(result))
                }
            }
            override fun onError(code: String, message: String) {
                mainHandler.post {
                    pendingPromises.remove(sessionId)?.reject(code, message)
                }
            }
        }

    private fun voiceResultToMap(r: VoiceResult): WritableMap =
        Arguments.createMap().apply {
            putString("sessionId", r.sessionId)
            putDouble("turn", r.turn.toDouble())
            putString("intent", r.intent.name)
            putDouble("confidence", r.confidence.toDouble())
            putBoolean("isComplete", r.isComplete)
            putMap("slotsCollected", Arguments.createMap().apply {
                r.slotsCollected.forEach { (k, v) ->
                    when (v) {
                        is Number -> putDouble(k, v.toDouble())
                        else -> putString(k, v.toString())
                    }
                }
            })
            putArray("slotsPending", Arguments.createArray().apply {
                r.slotsPending.forEach { pushString(it) }
            })
            putString("responseTextTarget", r.responseTextTarget)
            putString("responseTextEnglish", r.responseTextEnglish)
            if (r.audioFilePath != null) putString("audioFilePath", r.audioFilePath)
            else putNull("audioFilePath")
            putDouble("audioSampleRate", r.audioSampleRate.toDouble())
            putDouble("processingTimeMs", r.processingTimeMs.toDouble())
            putString("language", r.language.name)
            r.requiresConfirmation?.let { putBoolean("requiresConfirmation", it) }
        }

    private fun mapToSDKConfig(m: ReadableMap?): SDKConfig {
        if (m == null) return SDKConfig()
        return SDKConfig(
            targetLanguage = optEnum(m, "targetLanguage", Language.HINDI),
            ttsEnabled = optBoolean(m, "ttsEnabled", true),
            ttsEnabledForTextMode = optBoolean(m, "ttsEnabledForTextMode", false),
            intentAcceptThreshold = optDouble(m, "intentAcceptThreshold", 0.85).toFloat(),
            intentRejectThreshold = optDouble(m, "intentRejectThreshold", 0.45).toFloat(),
            maxClarificationTurns = optInt(m, "maxClarificationTurns", 3),
            storageQuotaMB = optInt(m, "storageQuotaMB", 450),
            customVocabulary = readableArrayToStringList(m.getArray("customVocabulary")),
            modelLocalBasePath = if (m.hasKey("modelLocalBasePath")) m.getString("modelLocalBasePath") else null,
            modelDownloadBaseUrl = if (m.hasKey("modelDownloadBaseUrl")) m.getString("modelDownloadBaseUrl") else null,
            useContinuousRecording = optBoolean(m, "useContinuousRecording", false),
            useHardcodedIntent = optBoolean(m, "useHardcodedIntent", false),
            hardcodedIntent = optIntent(m, "hardcodedIntent", Intent.CHECK_BALANCE)
        )
    }

    private fun optIntent(m: ReadableMap, key: String, default: Intent): Intent {
        val s = m.getString(key) ?: return default
        return try {
            Intent.valueOf(s)
        } catch (_: Exception) {
            default
        }
    }

    private fun optBoolean(m: ReadableMap, key: String, default: Boolean): Boolean {
        return if (m.hasKey(key)) m.getBoolean(key) else default
    }

    private fun optDouble(m: ReadableMap, key: String, default: Double): Double {
        return if (m.hasKey(key)) m.getDouble(key) else default
    }

    private fun optInt(m: ReadableMap, key: String, default: Int): Int {
        return if (m.hasKey(key)) m.getInt(key) else default
    }

    private fun optEnum(m: ReadableMap, key: String, default: Language): Language {
        val s = m.getString(key) ?: return default
        return try {
            Language.valueOf(s)
        } catch (_: Exception) {
            default
        }
    }

    private fun readableArrayToStringList(arr: com.facebook.react.bridge.ReadableArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.size()).map { arr.getString(it) ?: "" }
    }

    private fun emitSDKReady(languages: List<Language>, readyModelNames: List<String>) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("SDK_READY", Arguments.createArray().apply {
                languages.forEach { pushString(it.name) }
            })
    }

    private fun emitInitProgress(message: String) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("SDK_INIT_PROGRESS", Arguments.createMap().apply { putString("message", message) })
    }

    private fun emitAudioLevel(level: Float) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("SDK_AUDIO_LEVEL", level.toDouble())
    }

    private fun emitLanguageDownloadProgress(
        language: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        percentage: Int,
        status: String
    ) {
        val map = Arguments.createMap().apply {
            putString("language", language)
            putDouble("bytesDownloaded", bytesDownloaded.toDouble())
            putDouble("totalBytes", totalBytes.toDouble())
            putDouble("percentage", percentage.toDouble())
            putString("status", status)
        }
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("SDK_LANGUAGE_DOWNLOAD_PROGRESS", map)
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun runOnExecutor(block: () -> Unit) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(block)
    }
}
