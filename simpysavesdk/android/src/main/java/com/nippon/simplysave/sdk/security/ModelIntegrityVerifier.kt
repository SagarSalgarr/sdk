package com.nippon.simplysave.sdk.security

import com.nippon.simplysave.sdk.models.ModelRegistry
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Verifies SHA-256 of each file in sdk_models against integrity.json.
 * Runs in background after SDK ready; marks CORRUPTED and rolls back to previous/ if healthy.
 * Uses streaming hash for large files to avoid OOM.
 */
class ModelIntegrityVerifier(
    private val context: android.content.Context,
    private val registry: ModelRegistry
) {
    private val filesDir = context.filesDir
    private val sdkModelsRoot = File(filesDir, "sdk_models")

    fun verifyAll(onCorrupted: (logicalName: String) -> Unit) {
        val modelDirs = sdkModelsRoot.listFiles() ?: return
        for (dir in modelDirs) {
            if (!dir.isDirectory) continue
            val activeDir = File(dir, "active")
            val integrityFile = File(activeDir, "integrity.json")
            if (!integrityFile.exists()) continue
            try {
                val json = JSONObject(integrityFile.readText())
                var failed = false
                for (key in json.keys()) {
                    val expectedHash = json.optString(key)
                    if (expectedHash.isEmpty()) continue
                    val file = File(activeDir, key)
                    if (!file.exists()) { failed = true; break }
                    val actualHash = sha256File(file)
                    if (actualHash != expectedHash) {
                        failed = true
                        break
                    }
                }
                if (failed) onCorrupted(dir.name)
            } catch (_: Exception) { }
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Hash file in chunks to avoid OOM on large model files. */
    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun computeIntegrityJson(files: List<Pair<String, ByteArray>>): String {
            val obj = JSONObject()
            val md = MessageDigest.getInstance("SHA-256")
            for ((name, bytes) in files) {
                md.reset()
                md.update(bytes)
                obj.put(name, md.digest().joinToString("") { "%02x".format(it) })
            }
            return obj.toString(2)
        }

        /** Compute integrity JSON from files on disk (streaming hash). Use for large downloaded files to avoid OOM. */
        fun computeIntegrityJsonFromFiles(files: List<Pair<String, File>>): String {
            val obj = JSONObject()
            val md = MessageDigest.getInstance("SHA-256")
            for ((name, file) in files) {
                if (!file.exists()) continue
                md.reset()
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        md.update(buf, 0, n)
                    }
                }
                obj.put(name, md.digest().joinToString("") { "%02x".format(it) })
            }
            return obj.toString(2)
        }
    }
}
