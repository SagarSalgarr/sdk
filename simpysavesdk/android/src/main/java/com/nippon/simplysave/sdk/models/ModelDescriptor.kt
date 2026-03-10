package com.nippon.simplysave.sdk.models

import com.nippon.simplysave.sdk.Language

/**
 * Read-only descriptor for one model. Populated from model-registry.json.
 * The registry answers: given task type and language, which descriptor to use.
 */
data class ModelDescriptor(
    val logicalName: String,
    val modelType: ModelType,
    val runtime: String,
    val fileNames: List<String>,
    val assetSource: AssetSource,
    val assetPackName: String,
    /** Base URL for REMOTE_DOWNLOAD (e.g. https://cdn.example.com/simplysave-models). Trailing slash optional. */
    val downloadBaseUrl: String?,
    val supportedLanguages: List<String>,
    val version: String,
    val minimumRamMB: Int,
    val quantizationType: String,
    val priority: Int
) {
    companion object {
        private val languageToCode = mapOf(
            com.nippon.simplysave.sdk.Language.ENGLISH to "en",
            com.nippon.simplysave.sdk.Language.HINDI to "hi",
            com.nippon.simplysave.sdk.Language.BENGALI to "bn",
            com.nippon.simplysave.sdk.Language.GUJARATI to "gu",
            com.nippon.simplysave.sdk.Language.MARATHI to "mr",
            com.nippon.simplysave.sdk.Language.ODIA to "or",
            com.nippon.simplysave.sdk.Language.ASSAMESE to "as",
            com.nippon.simplysave.sdk.Language.BHOJPURI to "bho",
            com.nippon.simplysave.sdk.Language.MAITHILI to "mai"
        )
    }

    fun supportsLanguage(language: Language): Boolean {
        val code = languageToCode[language] ?: language.name.lowercase().take(2)
        return supportedLanguages.any { it.equals("all", ignoreCase = true) || it.equals(code, ignoreCase = true) }
    }
}
