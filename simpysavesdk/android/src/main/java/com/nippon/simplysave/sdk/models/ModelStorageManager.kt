package com.nippon.simplysave.sdk.models

import android.content.Context
import com.nippon.simplysave.sdk.security.CryptoManager
import java.io.File

/**
 * The only class that knows where model files live. Everyone else asks for decrypted bytes.
 * getDecryptedBytes(logicalName, fileName) → ByteArray. Caller never sees paths or keys.
 */
class ModelStorageManager(private val context: Context, private val registry: ModelRegistry) {

    private val filesDir: File = context.filesDir
    private val sdkModelsRoot = File(filesDir, "sdk_models")

    fun getDecryptedBytes(logicalName: String, fileName: String): ByteArray {
        val descriptor = registry.getModelByLogicalName(logicalName) ?: throw IllegalStateException("Unknown model: $logicalName")
        val encryptedBytes = when (descriptor.assetSource) {
            AssetSource.AAR_ASSETS -> context.assets.open(fileName).readBytes()
            AssetSource.PLAY_FEATURE_DELIVERY,
            AssetSource.PLAY_ASSET_DELIVERY_ON_DEMAND,
            AssetSource.REMOTE_DOWNLOAD,
            AssetSource.LOCAL_PATH -> {
                val file = File(File(sdkModelsRoot, logicalName), "active/$fileName")
                if (!file.exists()) throw IllegalStateException("Model file not found: $logicalName/$fileName")
                val metadataFile = File(File(sdkModelsRoot, logicalName), "metadata.json")
                val storedPlain = try {
                    if (metadataFile.exists()) {
                        val json = org.json.JSONObject(metadataFile.readText())
                        json.optBoolean("encrypted", true) == false
                    } else false
                } catch (_: Exception) { false }
                val bytes = file.readBytes()
                if (storedPlain) bytes else CryptoManager.decrypt(bytes, context)
            }
        }
        return if (descriptor.assetSource == AssetSource.AAR_ASSETS) CryptoManager.decrypt(encryptedBytes, context) else encryptedBytes
    }

    fun hasModelFiles(logicalName: String): Boolean {
        val descriptor = registry.getModelByLogicalName(logicalName) ?: return false
        return when (descriptor.assetSource) {
            AssetSource.AAR_ASSETS -> descriptor.fileNames.all { fileName ->
                try {
                    context.assets.open(fileName).close()
                    true
                } catch (_: Exception) {
                    false
                }
            }
            else -> {
                val activeDir = File(sdkModelsRoot, "$logicalName/active")
                descriptor.fileNames.all { File(activeDir, it).exists() }
            }
        }
    }
}
