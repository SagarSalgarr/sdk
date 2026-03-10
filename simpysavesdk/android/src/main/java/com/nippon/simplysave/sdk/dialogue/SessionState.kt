package com.nippon.simplysave.sdk.dialogue

import com.nippon.simplysave.sdk.Intent

/**
 * Holds per-session conversation state: current intent, slots, turn count.
 */
data class SessionState(
    val sessionId: String,
    var currentIntent: Intent? = null,
    var slotsCollected: MutableMap<String, Any> = mutableMapOf(),
    var turn: Int = 0,
    var clarificationCount: Int = 0,
    var requiresConfirmation: Boolean = false,
    var pendingConfirmationIntent: Intent? = null
)
