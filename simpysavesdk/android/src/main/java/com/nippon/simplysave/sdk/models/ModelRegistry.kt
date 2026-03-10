package com.nippon.simplysave.sdk.models

import android.content.Context
import com.nippon.simplysave.sdk.Language
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "hospital directory" — knows every model, its type, languages, and availability.
 * Populated at SDK init from model-registry.json. Read-only after init.
 * Answers: given task type and language, which model descriptor to use.
 */
class ModelRegistry(private val context: Context) {

    private val descriptorsByLogicalName = mutableMapOf<String, ModelDescriptor>()
    private var availableLogicalNames: Set<String> = emptySet()

    fun loadFromAssets() {
        context.assets.open("model-registry.json").use { stream ->
            val json = stream.bufferedReader().readText()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val d = parseDescriptor(obj)
                descriptorsByLogicalName[d.logicalName] = d
            }
        }
        availableLogicalNames = descriptorsByLogicalName.keys.toSet()
    }

    fun setAvailableModels(logicalNames: Set<String>) {
        availableLogicalNames = logicalNames
    }

    fun getAvailableModels(): Set<String> = availableLogicalNames

    /**
     * Primary query: which model should the pipeline use for this task and language?
     * Returns the best descriptor by modelType and language (version). Does not require the model
     * to be available yet — on-demand models (e.g. TTS per language) are downloaded when ensureLoaded is called.
     */
    fun getModelFor(taskType: ModelType, language: Language): ModelDescriptor? {
        val candidates = descriptorsByLogicalName.values
            .filter { it.modelType == taskType && it.supportsLanguage(language) }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { it.version }
    }

    fun getModelByLogicalName(logicalName: String): ModelDescriptor? =
        descriptorsByLogicalName[logicalName]

    /** All model descriptors from registry (enables pluggable models: add/change entries in model-registry.json). */
    fun getDescriptors(): List<ModelDescriptor> = descriptorsByLogicalName.values.toList()

    /** Logical names for models to ensure at init (STT + translation). TTS and other on-demand models are excluded. */
    fun getLogicalNamesForInit(): Set<String> = getDescriptors()
        .filter { it.modelType == ModelType.STT || it.modelType == ModelType.TRANSLATION_FROM_ENGLISH || it.modelType == ModelType.TRANSLATION_TO_ENGLISH }
        .map { it.logicalName }
        .toSet()

    fun getAllModelsForType(taskType: ModelType): List<ModelDescriptor> =
        descriptorsByLogicalName.values.filter { it.modelType == taskType }

    private fun parseDescriptor(obj: JSONObject): ModelDescriptor {
        val fileNames = mutableListOf<String>()
        obj.getJSONArray("fileNames").let { arr ->
            for (i in 0 until arr.length()) fileNames.add(arr.getString(i))
        }
        val supportedLanguages = mutableListOf<String>()
        obj.getJSONArray("supportedLanguages").let { arr ->
            for (i in 0 until arr.length()) supportedLanguages.add(arr.getString(i))
        }
        val assetSource = when (obj.getString("assetSource")) {
            "AAR_ASSETS" -> AssetSource.AAR_ASSETS
            "PLAY_ASSET_DELIVERY_ON_DEMAND" -> AssetSource.PLAY_ASSET_DELIVERY_ON_DEMAND
            "REMOTE_DOWNLOAD" -> AssetSource.REMOTE_DOWNLOAD
            "LOCAL_PATH" -> AssetSource.LOCAL_PATH
            else -> AssetSource.PLAY_FEATURE_DELIVERY
        }
        val downloadBaseUrl = if (obj.has("downloadBaseUrl")) obj.optString("downloadBaseUrl", null).takeIf { it.isNotEmpty() } else null
        return ModelDescriptor(
            logicalName = obj.getString("logicalName"),
            modelType = ModelType.valueOf(obj.getString("modelType")),
            runtime = obj.getString("runtime"),
            fileNames = fileNames,
            assetSource = assetSource,
            assetPackName = obj.optString("assetPackName", ""),
            downloadBaseUrl = downloadBaseUrl,
            supportedLanguages = supportedLanguages,
            version = obj.getString("version"),
            minimumRamMB = obj.getInt("minimumRamMB"),
            quantizationType = obj.optString("quantizationType", "FP16"),
            priority = obj.optInt("priority", 5)
        )
    }
}
