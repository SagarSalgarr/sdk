package com.nippon.simplysave.sdk.security

import android.content.Context
import android.content.pm.PackageManager

/**
 * Verifies that the calling APK is authorized (package name and signing cert match license).
 */
object CallerVerifier {

    fun verify(context: Context, expectedPackageName: String?, expectedCertFingerprint: String?): Result<Unit> {
        val actualPackage = context.packageName
        if (expectedPackageName != null && actualPackage != expectedPackageName) {
            return Result.failure(SecurityException("Unauthorized caller package"))
        }
        if (expectedCertFingerprint != null) {
            val actualFingerprint = getSigningCertificateSha256(context)
            if (actualFingerprint != null && actualFingerprint != expectedCertFingerprint) {
                return Result.failure(SecurityException("Certificate mismatch"))
            }
        }
        return Result.success(Unit)
    }

    private fun getSigningCertificateSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signature = packageInfo.signatures?.firstOrNull() ?: return null
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
