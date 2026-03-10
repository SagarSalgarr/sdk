package com.nippon.simplysave.sdk.config

import java.util.regex.Pattern

/**
 * Normalizes text before translation/TTS: e.g. "₹1000" → "one thousand rupees".
 * Ensures Indic models get verbalized numbers for natural output.
 */
object TextNormalizer {

    private val amountPattern = Pattern.compile("(?:(?:Rs\\.?|₹|INR)\\s*)?([\\d,]+(?:\\.\\d+)?)\\s*(?:rupees?|rs\\.?)?", Pattern.CASE_INSENSITIVE)

    fun normalizeForTranslation(text: String, language: com.nippon.simplysave.sdk.Language): String {
        var out = text
        val matcher = amountPattern.matcher(out)
        val sb = StringBuffer()
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val num = numStr.toDoubleOrNull() ?: continue
            val verbalized = verbalizeAmount(num.toLong(), language)
            matcher.appendReplacement(sb, verbalized)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun verbalizeAmount(amount: Long, language: com.nippon.simplysave.sdk.Language): String {
        if (amount == 0L) return "zero rupees"
        val parts = mutableListOf<String>()
        var n = amount
        val units = listOf("", "thousand", "lakh", "crore")
        var unitIndex = 0
        while (n > 0 && unitIndex < units.size) {
            val chunk = (n % 1000).toInt()
            n /= 1000
            if (chunk > 0) {
                val part = when (unitIndex) {
                    0 -> verbalizeHundreds(chunk)
                    1 -> "${verbalizeHundreds(chunk)} thousand"
                    2 -> "${verbalizeHundreds(chunk)} lakh"
                    3 -> "${verbalizeHundreds(chunk)} crore"
                    else -> verbalizeHundreds(chunk)
                }
                parts.add(0, part)
            }
            unitIndex++
        }
        val en = parts.joinToString(" ")
        return when (language) {
            com.nippon.simplysave.sdk.Language.HINDI -> "$en rupees"
            com.nippon.simplysave.sdk.Language.ENGLISH -> "$en rupees"
            else -> "$en rupees"
        }
    }

    private fun verbalizeHundreds(n: Int): String {
        if (n == 0) return ""
        val ones = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        val teens = arrayOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        return when {
            n >= 100 -> "${ones[n / 100]} hundred " + verbalizeHundreds(n % 100)
            n >= 20 -> (tens[n / 10] + " " + ones[n % 10]).trim()
            n >= 10 -> teens[n - 10]
            else -> ones[n]
        }
    }
}
