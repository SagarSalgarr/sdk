package com.nippon.simplysave.sdk.security

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Validates license JWT: parse claims (packageName, certificateFingerprint), verify caller.
 * Production: verify signature with Nippon public key (hardcoded byte array). Here we parse and call CallerVerifier.
 */
object LicenseValidator {

    private const val JWT_PARTS = 3

    fun validate(context: Context, licenseKey: String): Result<Unit> {
        if (licenseKey.isBlank()) return Result.failure(SecurityException("License key is required"))
        val packageName = context.packageName
        if (packageName.isBlank()) return Result.failure(SecurityException("Invalid caller context"))

        // Dev bypass: allow "dev" or "development" for local testing (no JWT verification).
        if (licenseKey == "dev" || licenseKey == "development") {
            return Result.success(Unit)
        }

        val parts = licenseKey.split(".")
        if (parts.size != JWT_PARTS) return Result.failure(SecurityException("Invalid JWT format"))

        val payloadJson = try {
            String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            return Result.failure(SecurityException("Invalid JWT payload"))
        }
        val payload = try {
            JSONObject(payloadJson)
        } catch (_: Exception) {
            return Result.failure(SecurityException("Invalid JWT claims"))
        }

        val exp = payload.optLong("exp", 0L)
        if (exp > 0 && exp < (System.currentTimeMillis() / 1000)) {
            return Result.failure(SecurityException("License expired"))
        }

        val expectedPackage = payload.optString("packageName", "").takeIf { it.isNotEmpty() }
        val expectedCert = payload.optString("certificateFingerprint", "").takeIf { it.isNotEmpty() }

        return CallerVerifier.verify(context, expectedPackage, expectedCert)
    }

    private fun base64UrlDecode(s: String): ByteArray {
        var base64 = s.replace('-', '+').replace('_', '/')
        when (base64.length % 4) {
            2 -> base64 += "=="
            3 -> base64 += "="
        }
        return Base64.decode(base64, Base64.DEFAULT)
    }
}
