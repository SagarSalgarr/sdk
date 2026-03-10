package com.nippon.simplysave.sdk.models

/**
 * One loaded model: descriptor + last-used timestamp + estimated RAM (MB).
 * Session/interpreter lives inside the inference engine; we only track readiness here.
 */
data class LoadedModel(
    val descriptor: ModelDescriptor,
    var lastUsedMs: Long = System.currentTimeMillis(),
    val estimatedRamMB: Int = 0,
    var state: ModelState = ModelState.READY
)
