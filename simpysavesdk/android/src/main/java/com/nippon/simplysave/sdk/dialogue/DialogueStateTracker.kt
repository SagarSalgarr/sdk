package com.nippon.simplysave.sdk.dialogue

import com.nippon.simplysave.sdk.Intent

/**
 * Tracks dialogue state per session. Thread-safe per sessionId.
 */
class DialogueStateTracker {

    private val sessions = mutableMapOf<String, SessionState>()
    private val lock = Any()

    fun getOrCreate(sessionId: String): SessionState =
        synchronized(lock) {
            sessions.getOrPut(sessionId) { SessionState(sessionId = sessionId) }
        }

    fun updateIntent(sessionId: String, intent: Intent) {
        synchronized(lock) {
            getOrCreate(sessionId).currentIntent = intent
        }
    }

    fun updateSlots(sessionId: String, slots: Map<String, Any>) {
        synchronized(lock) {
            val state = getOrCreate(sessionId)
            state.slotsCollected.putAll(slots)
        }
    }

    fun incrementTurn(sessionId: String): Int =
        synchronized(lock) {
            val state = getOrCreate(sessionId)
            state.turn += 1
            state.turn
        }

    fun incrementClarification(sessionId: String): Int =
        synchronized(lock) {
            val state = getOrCreate(sessionId)
            state.clarificationCount += 1
            state.clarificationCount
        }

    fun clearSession(sessionId: String) {
        synchronized(lock) {
            sessions.remove(sessionId)
        }
    }
}
