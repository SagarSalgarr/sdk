package com.nippon.simplysave.sdk.models

import com.nippon.simplysave.sdk.inference.IndicTransEngine
import com.nippon.simplysave.sdk.inference.IntentClassifierEngine
import com.nippon.simplysave.sdk.inference.WhisperEngine
import com.nippon.simplysave.sdk.inference.TTSRouter

/**
 * The "scheduling desk" — ensures a model is loaded before use, runs warmup, evicts under memory pressure.
 * Pipeline steps call ensureLoaded(descriptor) then getEngineForType(type); they never load models directly.
 * When a model is not yet on device, [delivery] is used to fetch it on demand (e.g. TTS only when user requests speech).
 */
class ModelLifecycleManager(
    private val registry: ModelRegistry,
    private val storage: ModelStorageManager,
    private val delivery: ModelDeliveryManager? = null
) {
    private val loadedModels = mutableMapOf<String, LoadedModel>()
    private val lock = Any()
    private val condition = Any()
    private var totalLoadedRamMB = 0
    private val maxRamMB = 200

    private val engineByType = mapOf(
        ModelType.STT to WhisperEngine,
        ModelType.TRANSLATION_TO_ENGLISH to IndicTransEngine,
        ModelType.TRANSLATION_FROM_ENGLISH to IndicTransEngine,
        ModelType.INTENT_CLASSIFICATION to IntentClassifierEngine,
        ModelType.TTS_ACOUSTIC to TTSRouter,
        ModelType.TTS_VOCODER to TTSRouter,
        ModelType.TTS_END_TO_END to TTSRouter
    )

    /**
     * Block until the model is READY (or ERROR). Called by pipeline steps on the inference thread.
     */
    fun ensureLoaded(descriptor: ModelDescriptor) {
        synchronized(lock) {
            val current = loadedModels[descriptor.logicalName]
            when (current?.state) {
                ModelState.READY -> {
                    current.lastUsedMs = System.currentTimeMillis()
                    return
                }
                ModelState.LOADING, ModelState.WARMING_UP -> {
                    while (loadedModels[descriptor.logicalName]?.state == ModelState.LOADING ||
                        loadedModels[descriptor.logicalName]?.state == ModelState.WARMING_UP
                    ) {
                        synchronized(condition) { (condition as java.lang.Object).wait() }
                    }
                    if (loadedModels[descriptor.logicalName]?.state == ModelState.ERROR) {
                        throw ModelLoadException("Model failed to load: ${descriptor.logicalName}")
                    }
                    return
                }
                ModelState.ERROR -> throw ModelLoadException("Model in error state: ${descriptor.logicalName}")
                else -> { /* UNLOADED or UNLOADING, proceed to load */ }
            }

            loadedModels[descriptor.logicalName] = LoadedModel(descriptor, state = ModelState.LOADING)
        }

        try {
            if (!storage.hasModelFiles(descriptor.logicalName) && delivery != null) {
                val ready = delivery.ensureModelsReady(setOf(descriptor.logicalName))
                registry.setAvailableModels(registry.getAvailableModels() + ready)
            }
            when (val engine = engineByType[descriptor.modelType]) {
                is WhisperEngine -> {
                    val bytes = if (storage.hasModelFiles(descriptor.logicalName)) {
                        descriptor.fileNames.map { storage.getDecryptedBytes(descriptor.logicalName, it) }
                    } else emptyList()
                    val enc = if (bytes.size >= 1) bytes[0] else null
                    val dec = if (bytes.size >= 2) bytes[1] else null
                    val vocab = if (bytes.size >= 3) bytes[2] else null
                    WhisperEngine.load(enc, dec, vocab)
                }
                is IndicTransEngine -> {
                    val bytes = if (storage.hasModelFiles(descriptor.logicalName)) {
                        descriptor.fileNames.map { storage.getDecryptedBytes(descriptor.logicalName, it) }
                    } else emptyList()
                    val sessionBytes = if (bytes.size >= 1) bytes[0] else null
                    val srcDict = descriptor.fileNames.indexOf("dict.SRC.json").let { i -> if (i >= 0 && i < bytes.size) bytes[i] else null }
                    val tgtDict = descriptor.fileNames.indexOf("dict.TGT.json").let { i -> if (i >= 0 && i < bytes.size) bytes[i] else null }
                    IndicTransEngine.load(sessionBytes, srcDict, tgtDict)
                }
                else -> {
                    // Intent, TTS: mark READY; engines use pass-through or load from path when wired.
                }
            }

            val ram = descriptor.minimumRamMB
            synchronized(lock) {
                loadedModels[descriptor.logicalName] = LoadedModel(
                    descriptor,
                    lastUsedMs = System.currentTimeMillis(),
                    estimatedRamMB = ram,
                    state = ModelState.READY
                )
                totalLoadedRamMB += ram
                evictIfOverBudget()
                synchronized(condition) { (condition as java.lang.Object).notifyAll() }
            }
        } catch (e: Exception) {
            synchronized(lock) {
                loadedModels[descriptor.logicalName] = LoadedModel(descriptor, state = ModelState.ERROR)
                synchronized(condition) { (condition as java.lang.Object).notifyAll() }
            }
            throw ModelLoadException("Load failed: ${descriptor.logicalName}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getEngineForType(type: ModelType): T? = engineByType[type] as? T

    private fun evictIfOverBudget() {
        if (totalLoadedRamMB <= maxRamMB) return
        val evictable = loadedModels.values
            .filter { it.state == ModelState.READY && it.descriptor.priority < 9 }
            .sortedBy { it.descriptor.priority * 1_000_000L - it.lastUsedMs }
            .firstOrNull() ?: return
        unload(evictable.descriptor.logicalName)
    }

    private fun unload(logicalName: String) {
        val loaded = loadedModels[logicalName] ?: return
        if (loaded.state != ModelState.READY) return
        loaded.state = ModelState.UNLOADING
        when (loaded.descriptor.modelType) {
            ModelType.STT -> WhisperEngine.unload()
            ModelType.TRANSLATION_FROM_ENGLISH, ModelType.TRANSLATION_TO_ENGLISH -> IndicTransEngine.unload()
            else -> { }
        }
        loadedModels.remove(logicalName)
        totalLoadedRamMB -= loaded.estimatedRamMB
        loaded.state = ModelState.UNLOADED
    }
}

class ModelLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
