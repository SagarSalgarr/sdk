package com.nippon.simplysave.sdk

/**
 * SDK runtime exception with machine-readable code.
 */
class SDKException(
    val code: String,
    override val message: String,
    val recoverable: Boolean = false
) : RuntimeException(message)
