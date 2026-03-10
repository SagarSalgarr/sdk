package com.nippon.simplysave.sdk.models

import android.content.Context
import android.util.Log
import com.nippon.simplysave.sdk.Language
import com.nippon.simplysave.sdk.security.CryptoManager
import com.nippon.simplysave.sdk.security.ModelIntegrityVerifier
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Ensures models are ready: lightweight from path (AAR or LOCAL_PATH), heavier from single base URL (REMOTE_DOWNLOAD).
 * - AAR_ASSETS / LOCAL_PATH: copy from assets or modelLocalBasePath to sdk_models/.../active/
 * - REMOTE_DOWNLOAD: one base URL (modelDownloadBaseUrl) where all such models are hosted; download on first use.
 */
class ModelDeliveryManager(
    private val context: Context,
    private val registry: ModelRegistry,
    /** Base path for LOCAL_PATH models: {modelLocalBasePath}/{logicalName}/{fileName}. */
    private val modelLocalBasePath: String? = null,
    /** Single base URL for all REMOTE_DOWNLOAD models. All hosted models use this one URL. */
    private val modelDownloadBaseUrl: String? = null
) {
    private val filesDir = context.filesDir
    private val sdkModelsRoot = File(filesDir, "sdk_models")

    private companion object {
        const val TAG = "ModelDelivery"
        const val MAX_DOWNLOAD_ATTEMPTS = 3
    }

    /**
     * Ensure models are ready (copied or downloaded). When [requiredLogicalNames] is null, all models from registry are ensured.
     * When non-null, only those logical names are ensured — use for on-demand (e.g. TTS for target language from frontend).
     * Model list is registry-driven: add/change models in model-registry.json without code changes.
     */
    fun ensureModelsReady(
        requiredLogicalNames: Set<String>? = null,
        onProgress: (message: String) -> Unit = {}
    ): Set<String> {
        val ready = mutableSetOf<String>()
        val allDescriptors = registry.getDescriptors()
        val descriptors = if (requiredLogicalNames == null) allDescriptors
            else allDescriptors.filter { it.logicalName in requiredLogicalNames }

        for (descriptor in descriptors) {
            val activeDir = File(File(sdkModelsRoot, descriptor.logicalName), "active")
            when (descriptor.assetSource) {
                AssetSource.AAR_ASSETS -> {
                    onProgress("Copying ${descriptor.logicalName}...")
                    if (copyFromAssets(descriptor, activeDir)) ready.add(descriptor.logicalName)
                }
                AssetSource.LOCAL_PATH -> {
                    val basePath = modelLocalBasePath
                    if (basePath != null) {
                        onProgress("Loading ${descriptor.logicalName} from path...")
                        if (copyFromLocalPath(descriptor, activeDir, File(basePath))) ready.add(descriptor.logicalName)
                    } else {
                        val allExist = descriptor.fileNames.all { File(activeDir, it).exists() }
                        if (allExist) ready.add(descriptor.logicalName)
                    }
                }
                AssetSource.REMOTE_DOWNLOAD -> {
                    val baseUrl = modelDownloadBaseUrl
                    val allExist = descriptor.fileNames.all { File(activeDir, it).exists() }
                    if (allExist) {
                        onProgress("Using cached ${descriptor.logicalName}")
                        Log.d(TAG, "Model already on device: ${descriptor.logicalName}")
                        ready.add(descriptor.logicalName)
                    } else if (baseUrl != null) {
                        val firstUrl = "${baseUrl.trimEnd('/')}/${descriptor.logicalName}/${descriptor.fileNames.firstOrNull() ?: ""}"
                        Log.d(TAG, "Downloading model: ${descriptor.logicalName} from baseUrl=$baseUrl firstUrl=$firstUrl")
                        onProgress("Downloading ${descriptor.logicalName}...")
                        if (downloadToActive(descriptor, activeDir, baseUrl, onProgress)) {
                            Log.d(TAG, "Model ready: ${descriptor.logicalName}")
                            ready.add(descriptor.logicalName)
                        } else {
                            Log.w(TAG, "Model download failed (will resume on next init): ${descriptor.logicalName}")
                        }
                    } else {
                        Log.w(TAG, "REMOTE_DOWNLOAD but modelDownloadBaseUrl is null; cannot download ${descriptor.logicalName}. Pass modelDownloadBaseUrl in SDK config.")
                    }
                }
                else -> {
                    val allExist = descriptor.fileNames.all { File(activeDir, it).exists() }
                    if (allExist) ready.add(descriptor.logicalName)
                }
            }
        }
        return ready
    }

    private fun copyFromLocalPath(
        descriptor: com.nippon.simplysave.sdk.models.ModelDescriptor,
        activeDir: File,
        basePath: File
    ): Boolean {
        val sourceDir = File(basePath, descriptor.logicalName)
        if (!sourceDir.isDirectory) return false
        activeDir.mkdirs()
        val filesWithContent = mutableListOf<Pair<String, ByteArray>>()
        for (fileName in descriptor.fileNames) {
            val sourceFile = File(sourceDir, fileName)
            if (!sourceFile.exists()) continue
            val plain = sourceFile.readBytes()
            if (plain.isEmpty()) continue
            try {
                val encrypted = CryptoManager.encrypt(plain, context)
                File(activeDir, fileName).writeBytes(encrypted)
                filesWithContent.add(fileName to encrypted)
            } catch (_: Exception) {
                File(activeDir, fileName).writeBytes(plain)
                filesWithContent.add(fileName to plain)
            }
        }
        if (filesWithContent.isEmpty()) return false
        val integrityJson = ModelIntegrityVerifier.computeIntegrityJson(filesWithContent)
        File(activeDir, "integrity.json").writeText(integrityJson)
        val metadata = org.json.JSONObject().apply {
            put("version", descriptor.version)
            put("installTime", System.currentTimeMillis())
        }
        File(activeDir.parentFile, "metadata.json").writeText(metadata.toString(2))
        return true
    }

    private fun downloadToActive(
        descriptor: com.nippon.simplysave.sdk.models.ModelDescriptor,
        activeDir: File,
        baseUrl: String,
        onProgress: (message: String) -> Unit
    ): Boolean {
        activeDir.mkdirs()
        val base = baseUrl.trimEnd('/')
        val filesWithContent = mutableListOf<Pair<String, File>>()
        for (fileName in descriptor.fileNames) {
            val url = "$base/${descriptor.logicalName}/$fileName"
            val tempFile = File(activeDir, "$fileName.tmp")
            var attempt = 0
            var ok = false
            while (attempt < MAX_DOWNLOAD_ATTEMPTS && !ok) {
                attempt++
                val existingLength = if (tempFile.exists()) tempFile.length() else 0L
                if (existingLength > 0L) {
                    onProgress("Resuming $fileName (${existingLength / (1024 * 1024)} MB)... attempt $attempt")
                    Log.d(TAG, "Resume $fileName from ${existingLength / (1024 * 1024)} MB (attempt $attempt)")
                } else {
                    onProgress("Fetching $fileName... attempt $attempt")
                    Log.d(TAG, "Fetch $fileName (attempt $attempt)")
                }
                ok = try {
                    downloadWithResume(url, tempFile, existingLength, onProgress)
                } catch (e: Exception) {
                    Log.w(TAG, "Download attempt $attempt failed: $fileName - ${e.message}")
                    false
                }
                if (!ok && attempt < MAX_DOWNLOAD_ATTEMPTS) {
                    onProgress("Retrying $fileName in 2s...")
                    Thread.sleep(2000)
                }
            }
            if (!ok) continue
            if (tempFile.length() == 0L) {
                tempFile.delete()
                continue
            }
            // Stream-copy to final path. Do NOT readBytes() here — 280MB files cause OOM and kill the app.
            val finalFile = File(activeDir, fileName)
            try {
                tempFile.inputStream().use { input ->
                    finalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Copy failed: $fileName - ${e.message}")
                finalFile.delete()
                continue
            }
            tempFile.delete()
            filesWithContent.add(fileName to finalFile)
        }
        if (filesWithContent.isEmpty()) return false
        val integrityJson = ModelIntegrityVerifier.computeIntegrityJsonFromFiles(filesWithContent)
        File(activeDir, "integrity.json").writeText(integrityJson)
        val metadata = org.json.JSONObject().apply {
            put("version", descriptor.version)
            put("installTime", System.currentTimeMillis())
            put("encrypted", false) // Large REMOTE_DOWNLOAD files stored plain to avoid OOM
        }
        File(activeDir.parentFile, "metadata.json").writeText(metadata.toString(2))
        return true
    }

    /**
     * Download with optional resume. If [existingLength] > 0, sends Range header and appends to file.
     * Python http.server supports Range. Returns true if download completed (full or resumed).
     */
    private fun downloadWithResume(
        urlString: String,
        dest: File,
        existingLength: Long,
        onProgress: (message: String) -> Unit
    ): Boolean {
        val conn = java.net.URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 600_000 // 10 min for large files
        if (existingLength > 0L) {
            conn.setRequestProperty("Range", "bytes=$existingLength-")
        }
        conn.connect()
        val code = conn.responseCode
        val append = code == 206 && existingLength > 0L
        if (code != 200 && code != 206) {
            return false
        }
        conn.getInputStream().use { input ->
            streamToFile(input, dest, append)
        }
        Log.d(TAG, "Download complete: ${dest.name}")
        return true
    }

    private fun streamToFile(input: InputStream, dest: File, append: Boolean = false) {
        FileOutputStream(dest, append).use { out ->
            val buf = ByteArray(64 * 1024) // 64 KB
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                out.write(buf, 0, n)
            }
        }
    }

    private fun copyFromAssets(descriptor: com.nippon.simplysave.sdk.models.ModelDescriptor, activeDir: File): Boolean {
        activeDir.mkdirs()
        val filesWithContent = mutableListOf<Pair<String, ByteArray>>()
        for (fileName in descriptor.fileNames) {
            val plain = try {
                context.assets.open(fileName).readBytes()
            } catch (_: Exception) {
                try {
                    context.assets.open(fileName.replace(".enc", "")).readBytes()
                } catch (_: Exception) {
                    continue
                }
            }
            try {
                val encrypted = CryptoManager.encrypt(plain, context)
                File(activeDir, fileName).writeBytes(encrypted)
                filesWithContent.add(fileName to encrypted)
            } catch (_: Exception) {
                File(activeDir, fileName).writeBytes(plain)
                filesWithContent.add(fileName to plain)
            }
        }
        if (filesWithContent.isEmpty()) return false
        val integrityJson = ModelIntegrityVerifier.computeIntegrityJson(filesWithContent)
        File(activeDir, "integrity.json").writeText(integrityJson)
        val metadata = JSONObject().apply {
            put("version", descriptor.version)
            put("installTime", System.currentTimeMillis())
        }
        File(activeDir.parentFile, "metadata.json").writeText(metadata.toString(2))
        return true
    }

    fun prepareLanguage(language: Language, onProgress: (Long, Long, String) -> Unit, onReady: () -> Unit) {
        onProgress(0, 100, "PENDING")
        onProgress(100, 100, "COMPLETE")
        onReady()
    }
}
