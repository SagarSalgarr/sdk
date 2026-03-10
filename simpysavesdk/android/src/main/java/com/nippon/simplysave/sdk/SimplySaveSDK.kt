package com.nippon.simplysave.sdk

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.nippon.simplysave.sdk.config.ResponseTemplateReader
import com.nippon.simplysave.sdk.config.SlotSchemaReader
import com.nippon.simplysave.sdk.dialogue.DialogueStateTracker
import com.nippon.simplysave.sdk.models.ModelLifecycleManager
import com.nippon.simplysave.sdk.models.ModelRegistry
import com.nippon.simplysave.sdk.models.ModelStorageManager
import com.nippon.simplysave.sdk.models.ModelDeliveryManager
import com.nippon.simplysave.sdk.models.ModelType
import com.nippon.simplysave.sdk.pipeline.TextPipeline
import com.nippon.simplysave.sdk.pipeline.VoicePipeline
import com.nippon.simplysave.sdk.security.LicenseValidator
import com.nippon.simplysave.sdk.security.ModelIntegrityVerifier
import com.nippon.simplysave.sdk.telemetry.TelemetryCollector
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single public entry point for the native SDK. Singleton, thread-safe.
 */
class SimplySaveSDK private constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val initialized = AtomicBoolean(false)
    private var appContext: Context? = null
    private var config: SDKConfig = SDKConfig()
    private val dialogueTracker = DialogueStateTracker()
    private var modelRegistry: ModelRegistry? = null
    private var modelLifecycleManager: ModelLifecycleManager? = null
    private var audioRecorder: com.nippon.simplysave.sdk.audio.AudioRecorder? = null
    private var recordingSessionId: String? = null
    private var audioLevelCallback: ((Float) -> Unit)? = null

    companion object {
        @Volatile
        private var instance: SimplySaveSDK? = null

        fun getInstance(): SimplySaveSDK {
            return instance ?: synchronized(this) {
                instance ?: SimplySaveSDK().also { instance = it }
            }
        }
    }

    fun initialize(
        context: Context,
        licenseKey: String,
        config: SDKConfig,
        onProgress: (String) -> Unit = {},
        callback: SDKInitCallback
    ) {
        executor.execute {
            try {
                LicenseValidator.validate(context, licenseKey).getOrThrow()
                this.appContext = context.applicationContext
                this.config = config
                loadAssets(context, onProgress)
                initialized.set(true)
                val languages = Language.entries.filter { it != Language.ENGLISH }
                val readyModelNames = modelRegistry!!.getAvailableModels().toList()
                mainHandler.post { callback.onReady(languages, readyModelNames) }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("UNAUTHORIZED_CALLER", e.message ?: "Initialization failed") }
            }
        }
    }

    private fun loadAssets(context: Context, onProgress: (String) -> Unit = {}) {
        context.assets.open("slot-schemas.json").use { SlotSchemaReader.loadFromStream(it) }
        context.assets.open("response-templates.json").use { ResponseTemplateReader.loadFromStream(it) }
        modelRegistry = ModelRegistry(context).apply { loadFromAssets() }
        val storage = ModelStorageManager(context, modelRegistry!!)
        val delivery = ModelDeliveryManager(context, modelRegistry!!, config.modelLocalBasePath, config.modelDownloadBaseUrl)
        // Base pack: STT + translation only (from registry). TTS (e.g. MMS-TTS per language) is downloaded on demand when user requests speech for a language.
        val baseModels = modelRegistry!!.getLogicalNamesForInit()
        val ready = delivery.ensureModelsReady(requiredLogicalNames = baseModels, onProgress = onProgress)
        modelRegistry!!.setAvailableModels(ready)
        modelLifecycleManager = ModelLifecycleManager(modelRegistry!!, storage, delivery)
        ModelIntegrityVerifier(context, modelRegistry!!).verifyAll { logicalName ->
            modelRegistry!!.setAvailableModels(ready - logicalName)
        }
    }

    fun processVoice(audioUri: Uri, sessionId: String, language: Language, callback: SDKCallback) {
        executor.execute {
            try {
                val ctx = appContext ?: run {
                    callback.onError("SDK_NOT_INITIALIZED", "SDK not initialized")
                    return@execute
                }
                val bytes = when {
                    audioUri.scheme == "content" -> ctx.contentResolver.openInputStream(audioUri)?.readBytes()
                    audioUri.path != null -> java.io.File(audioUri.path).takeIf { it.exists() }?.readBytes()
                    else -> null
                } ?: run {
                    callback.onError("INVALID_AUDIO_FORMAT", "Could not read audio file")
                    return@execute
                }
                val cacheDir = File(ctx.cacheDir, "sdk_tts_output").apply { mkdirs() }
                val pipeline = VoicePipeline(
                    sessionId, language, config, ctx.cacheDir, dialogueTracker,
                    modelRegistry!!, modelLifecycleManager!!
                )
                pipeline.run(bytes, callback)
            } catch (e: Exception) {
                callback.onError("INFERENCE_FAILED", e.message ?: "Processing failed")
            }
        }
    }

    fun processText(text: String, sessionId: String, language: Language, callback: SDKCallback) {
        executor.execute {
            try {
                val ctx = appContext ?: run {
                    callback.onError("SDK_NOT_INITIALIZED", "SDK not initialized")
                    return@execute
                }
                val pipeline = TextPipeline(
                    sessionId, language, config, ctx.cacheDir, dialogueTracker,
                    modelRegistry!!, modelLifecycleManager!!
                )
                pipeline.run(text, callback)
            } catch (e: Exception) {
                callback.onError("INFERENCE_FAILED", e.message ?: "Processing failed")
            }
        }
    }

    fun prepareLanguage(context: Context, language: Language, onReady: () -> Unit) {
        executor.execute {
            onReady()
        }
    }

    fun setLanguage(language: Language) {
        config = config.copy(targetLanguage = language)
    }

    fun isLanguageAvailable(language: Language): Boolean = true

    fun getSupportedLanguages(): List<Language> =
        Language.entries.filter { it != Language.ENGLISH }

    /**
     * Returns storage path and per-model sizes for downloaded/cached models.
     * Call after initialize. Path is app's files dir + "sdk_models" (e.g. /data/data/<package>/files/sdk_models).
     */
    fun getModelStorageInfo(): ModelStorageInfo? {
        val ctx = appContext ?: return null
        val root = File(ctx.filesDir, "sdk_models")
        if (!root.exists() || !root.isDirectory) return ModelStorageInfo(root.absolutePath, emptyList(), 0L)
        val models = mutableListOf<ModelInfo>()
        var totalBytes = 0L
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val activeDir = File(dir, "active")
            if (!activeDir.exists()) return@forEach
            var sizeBytes = 0L
            var fileCount = 0
            activeDir.listFiles()?.forEach { f ->
                if (f.isFile && !f.name.endsWith(".tmp")) {
                    sizeBytes += f.length()
                    fileCount++
                }
            }
            if (fileCount > 0) {
                models.add(ModelInfo(dir.name, sizeBytes, fileCount))
                totalBytes += sizeBytes
            }
        }
        return ModelStorageInfo(root.absolutePath, models, totalBytes)
    }

    fun startRecording(sessionId: String, onAudioLevel: (Float) -> Unit) {
        executor.execute {
            val ctx = appContext ?: return@execute
            recordingSessionId = sessionId
            audioLevelCallback = onAudioLevel
            audioRecorder = com.nippon.simplysave.sdk.audio.AudioRecorder(ctx, onAudioLevel, config.useContinuousRecording)
            audioRecorder?.startRecording()
        }
    }

    fun stopRecordingAndProcess(language: Language, callback: SDKCallback) {
        executor.execute {
            val sid = recordingSessionId ?: "recording-session"
            recordingSessionId = null
            audioLevelCallback = null
            val wavBytes = audioRecorder?.stopAndGetWavBytes()
            audioRecorder = null
            if (wavBytes == null || wavBytes.isEmpty()) {
                callback.onError("INVALID_AUDIO_FORMAT", "No audio captured")
                return@execute
            }
            val ctx = appContext ?: run {
                callback.onError("SDK_NOT_INITIALIZED", "SDK not initialized")
                return@execute
            }
            val pipeline = VoicePipeline(
                sid, language, config, ctx.cacheDir, dialogueTracker,
                modelRegistry!!, modelLifecycleManager!!
            )
            pipeline.run(wavBytes, callback)
        }
    }

    fun destroy() {
        executor.execute {
            audioRecorder = null
            recordingSessionId = null
            audioLevelCallback = null
            appContext?.cacheDir?.resolve("sdk_tts_output")?.listFiles()?.forEach { it.delete() }
            initialized.set(false)
        }
    }
}

data class ModelStorageInfo(val storagePath: String, val models: List<ModelInfo>, val totalSizeBytes: Long)
data class ModelInfo(val logicalName: String, val sizeBytes: Long, val fileCount: Int)
