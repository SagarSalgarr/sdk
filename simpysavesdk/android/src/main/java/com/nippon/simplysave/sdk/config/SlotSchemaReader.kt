package com.nippon.simplysave.sdk.config

import org.json.JSONObject
import java.io.InputStream

/**
 * Reads slot-schemas.json from assets. Defines required/optional slots per intent.
 */
object SlotSchemaReader {

    data class SlotDef(val name: String, val required: Boolean, val type: String)

    private var schemaByIntent: Map<String, List<SlotDef>> = emptyMap()

    fun loadFromStream(stream: InputStream) {
        val json = stream.bufferedReader().readText()
        val root = JSONObject(json)
        schemaByIntent = root.keys().asSequence().associate { intent ->
            intent to root.getJSONArray(intent).let { arr ->
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    SlotDef(
                        name = obj.getString("name"),
                        required = obj.optBoolean("required", true),
                        type = obj.optString("type", "string")
                    )
                }
            }
        }
    }

    fun getSlotsForIntent(intent: String): List<SlotDef> =
        schemaByIntent[intent] ?: emptyList()

    fun getRequiredSlotNames(intent: String): List<String> =
        getSlotsForIntent(intent).filter { it.required }.map { it.name }
}
