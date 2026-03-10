package com.nippon.simplysave.sdk.telemetry

/**
 * Collects metric events (latency, model load, intent distribution) for debugging and analytics.
 * No user content in production; events are type + payload (counts, durations).
 */
object TelemetryCollector {

    data class MetricEvent(
        val timestampMs: Long = System.currentTimeMillis(),
        val type: String,
        val payload: Map<String, Any> = emptyMap()
    )

    private val events = mutableListOf<MetricEvent>()
    private const val MAX_EVENTS = 500

    fun record(type: String, vararg pairs: Pair<String, Any>) {
        synchronized(events) {
            events.add(MetricEvent(type = type, payload = pairs.toMap()))
            if (events.size > MAX_EVENTS) events.removeAt(0)
        }
    }

    fun recordPipelineLatency(sessionId: String, ms: Long) {
        record("pipeline_latency_ms", "session_id" to sessionId, "ms" to ms)
    }

    fun recordModelLoad(logicalName: String, ms: Long) {
        record("model_load_ms", "model" to logicalName, "ms" to ms)
    }

    fun recordIntent(intent: String, confidence: Float) {
        record("intent", "intent" to intent, "confidence" to confidence)
    }

    fun getRecentEvents(limit: Int = 100): List<MetricEvent> =
        synchronized(events) { events.takeLast(limit) }

    fun clear() = synchronized(events) { events.clear() }
}
