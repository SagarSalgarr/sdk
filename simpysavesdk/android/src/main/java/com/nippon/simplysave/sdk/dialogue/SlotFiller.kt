package com.nippon.simplysave.sdk.dialogue

import com.nippon.simplysave.sdk.Intent
import com.nippon.simplysave.sdk.config.SlotSchemaReader
import java.util.regex.Pattern

/**
 * Extracts slot values from English text using keywords and regex.
 */
object SlotFiller {

    private val amountPattern = Pattern.compile("(?:(?:Rs\\.?|₹|INR)\\s*)?([\\d,]+(?:\\.\\d+)?)\\s*(?:rupees?|rs\\.?)?", Pattern.CASE_INSENSITIVE)
    private val gullakKeywords = mapOf(
        "BUDHAPE_KA_SAHARA" to listOf("budhape ka sahara", "budhape", "retirement", "old age"),
        "SMART_GOLD" to listOf("smart gold", "gold", "gold gullak"),
        "BRIGHT_BHAVISHYA" to listOf("bright bhavishya", "child", "education", "bhavishya"),
        "EMERGENCY" to listOf("emergency", "emergency fund")
    )
    private val frequencyKeywords = mapOf(
        "MONTHLY" to listOf("monthly", "every month", "per month"),
        "WEEKLY" to listOf("weekly", "every week")
    )

    fun fillSlots(intent: Intent, englishText: String, existingSlots: Map<String, Any>): Pair<Map<String, Any>, List<String>> {
        val slots = existingSlots.toMutableMap()
        val slotDefs = SlotSchemaReader.getSlotsForIntent(intent.name)
        val pending = mutableListOf<String>()

        for (def in slotDefs) {
            if (slots.containsKey(def.name)) continue
            val value = when (def.name) {
                "amount" -> extractAmount(englishText)
                "gullak_type" -> extractGullakType(englishText)
                "frequency" -> extractFrequency(englishText)
                else -> null
            }
            if (value != null) {
                slots[def.name] = value
            } else if (def.required) {
                pending.add(def.name)
            }
        }

        val requiredNames = SlotSchemaReader.getRequiredSlotNames(intent.name)
        pending.clear()
        requiredNames.filter { !slots.containsKey(it) }.toCollection(pending)

        return slots to pending
    }

    private fun extractAmount(text: String): Any? {
        val m = amountPattern.matcher(text)
        if (m.find()) {
            val s = m.group(1)?.replace(",", "") ?: return null
            return s.toDoubleOrNull()?.toInt() ?: s.toIntOrNull()
        }
        val numPattern = Pattern.compile("(\\d+)\\s*(?:thousand|hundred|k)", Pattern.CASE_INSENSITIVE)
        val m2 = numPattern.matcher(text)
        if (m2.find()) {
            val num = m2.group(1)?.toIntOrNull() ?: return null
            if (text.contains("thousand", ignoreCase = true)) return num * 1000
            if (text.contains("hundred", ignoreCase = true)) return num * 100
            if (text.contains("k", ignoreCase = true)) return num * 1000
        }
        return null
    }

    private fun extractGullakType(text: String): String? {
        val t = text.lowercase()
        for ((key, keywords) in gullakKeywords) {
            if (keywords.any { t.contains(it) }) return key
        }
        return null
    }

    private fun extractFrequency(text: String): String? {
        val t = text.lowercase()
        for ((key, keywords) in frequencyKeywords) {
            if (keywords.any { t.contains(it) }) return key
        }
        return null
    }
}
