package com.nippon.simplysave.sdk.inference

import com.nippon.simplysave.sdk.Intent

/**
 * Intent classification: keyword + basic semantic matching only (no ML model).
 * Uses keyword lists, phrase matching, and simple synonym/vector-like overlap.
 */
object IntentClassifierEngine {

    private val intentKeywords = mapOf(
        Intent.CREATE_GULLAK_SIP to listOf(
            "sip", "monthly", "recurring", "start", "create", "gullak", "set up", "gullak start", "sip start"
        ),
        Intent.INVEST_LUMPSUM to listOf(
            "invest", "lumpsum", "one time", "amount", "rupees", "put", "gold", "budhape", "sahara",
            "smart gold", "bright bhavishya", "lump sum", "single payment"
        ),
        Intent.CHECK_BALANCE to listOf(
            "balance", "portfolio", "how much", "value", "total", "kitna hai", "check balance", "balance check"
        ),
        Intent.NOMINEE to listOf("nominee", "nomination", "nominee add", "nominee change"),
        Intent.NOTIFICATION to listOf("notification", "alerts", "notify", "alert", "sms", "reminder"),
        Intent.ECS_MANDATE to listOf("ecs", "mandate", "bank", "auto debit", "auto debit", "mandate register"),
        Intent.BRIGHT_BHAVISHYA to listOf("bright bhavishya", "child", "education", "bhavishya", "bacche ke liye")
    )

    /** Semantic variants / synonyms (optional expansion). */
    private val semanticVariants = mapOf(
        "sip" to listOf("sip", "recurring", "monthly invest"),
        "balance" to listOf("balance", "portfolio", "value", "total", "kitna", "amount invested"),
        "invest" to listOf("invest", "put", "lagana", "daalna", "buy", "purchase")
    )

    /**
     * Classify using keywords and basic semantic overlap. No model load required.
     */
    fun classify(englishText: String): Pair<Intent, Float> {
        val t = englishText.trim().lowercase()
        if (t.isBlank()) return Intent.FALLBACK to 0f

        val tokens = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        var bestIntent = Intent.FALLBACK
        var bestScore = 0.45f

        for ((intent, keywords) in intentKeywords) {
            val score = scoreIntent(t, tokens, keywords)
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }
        return bestIntent to bestScore.coerceIn(0f, 1f)
    }

    private fun scoreIntent(fullText: String, tokens: Set<String>, keywords: List<String>): Float {
        var hits = 0f
        for (kw in keywords) {
            if (fullText.contains(kw)) hits += 1f
            else if (kw.contains(" ") && fullText.contains(kw)) hits += 1f
            else {
                val kwTokens = kw.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (kwTokens.all { tokens.contains(it) }) hits += 0.8f
            }
        }
        val normalized = (hits / (keywords.size + 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        return (normalized * 0.5f + 0.5f).coerceIn(0f, 1f)
    }
}
