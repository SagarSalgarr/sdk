package com.nippon.simplysave.sdk.dialogue

import com.nippon.simplysave.sdk.Intent
import com.nippon.simplysave.sdk.config.ResponseTemplateReader
import java.util.regex.Pattern

/**
 * Fills response templates with slot values. Replaces {slotName} with value.
 */
object ResponseGenerator {

    fun fillTemplate(templateKey: String, slots: Map<String, Any>): String {
        var text = ResponseTemplateReader.getTemplate(templateKey) ?: return ""
        slots.forEach { (key, value) ->
            text = text.replace("{$key}", value.toString())
        }
        return text
    }

    fun getCompletionResponse(intent: Intent, slots: Map<String, Any>): String {
        val key = "${intent.name}_completion"
        return fillTemplate(key, slots).ifEmpty {
            ResponseTemplateReader.getTemplate("FALLBACK_completion") ?: "Done."
        }
    }

    fun getClarificationResponse(intent: Intent, slotName: String, slots: Map<String, Any>): String {
        val key = "${intent.name}_clarify_$slotName"
        var text = ResponseTemplateReader.getTemplate(key)
            ?: ResponseTemplateReader.getTemplate("clarify_generic")
            ?: "Could you provide more details?"
        slots.forEach { (k, v) -> text = text.replace("{$k}", v.toString()) }
        return text
    }

    fun getConfirmationResponse(intent: Intent, slots: Map<String, Any>): String {
        val key = "${intent.name}_confirm"
        return fillTemplate(key, slots).ifEmpty { "Please confirm." }
    }
}
