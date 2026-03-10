package com.nippon.simplysave.sdk.config

import org.json.JSONObject
import java.io.InputStream

/**
 * Reads response-templates.json from assets. Template key = intent or intent_slot.
 */
object ResponseTemplateReader {

    private var templates: Map<String, String> = emptyMap()

    fun loadFromStream(stream: InputStream) {
        val json = stream.bufferedReader().readText()
        val root = JSONObject(json)
        templates = root.keys().asSequence().associateWith { root.getString(it) }
    }

    fun getTemplate(key: String): String? = templates[key]

    fun getCompletionTemplate(intent: String): String? =
        getTemplate("${intent}_completion")

    fun getClarificationTemplate(intent: String, slotName: String): String? =
        getTemplate("${intent}_clarify_$slotName") ?: getTemplate("clarify_generic")

    fun getConfirmationTemplate(intent: String): String? =
        getTemplate("${intent}_confirm")
}
