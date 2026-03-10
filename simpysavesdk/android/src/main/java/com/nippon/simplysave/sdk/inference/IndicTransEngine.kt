package com.nippon.simplysave.sdk.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import com.nippon.simplysave.sdk.Language
import org.json.JSONObject

/**
 * Translation via IndicTrans2 ONNX (single encoder-decoder model).
 * Uses dict.SRC.json for encoding and dict.TGT.json for decoding. No fallback.
 */
object IndicTransEngine {

    private const val PAD_ID = 1
    private const val EOS_ID = 2
    private const val MAX_DECODE_LENGTH = 256

    private var session: OrtSession? = null
    private var srcTokenToId: Map<String, Int> = emptyMap()
    private var tgtIdToToken: Map<Int, String> = emptyMap()

    private val langToSrcTag = mapOf(
        Language.ENGLISH to "eng_Latn",
        Language.HINDI to "hin_Deva",
        Language.BENGALI to "ben_Beng",
        Language.GUJARATI to "guj_Gujr",
        Language.MARATHI to "mar_Deva",
        Language.ODIA to "or_Orya",
        Language.ASSAMESE to "asm_Beng",
        Language.BHOJPURI to "bho_Deva",
        Language.MAITHILI to "mai_Deva"
    )

    fun load(sessionBytes: ByteArray?, srcDictBytes: ByteArray?, tgtDictBytes: ByteArray?) {
        unload()
        if (sessionBytes == null || sessionBytes.isEmpty()) return
        session = OrtHelper.createSession(sessionBytes)
        if (srcDictBytes != null && srcDictBytes.isNotEmpty()) {
            val json = JSONObject(String(srcDictBytes, Charsets.UTF_8))
            srcTokenToId = mutableMapOf<String, Int>().apply {
                json.keys().forEach { key ->
                    put(key, json.getInt(key))
                }
            }
        }
        if (tgtDictBytes != null && tgtDictBytes.isNotEmpty()) {
            val json = JSONObject(String(tgtDictBytes, Charsets.UTF_8))
            tgtIdToToken = mutableMapOf<Int, String>().apply {
                json.keys().forEach { token ->
                    put(json.getInt(token), token)
                }
            }
        }
    }

    fun unload() {
        session?.close()
        session = null
        srcTokenToId = emptyMap()
        tgtIdToToken = emptyMap()
    }

    fun translate(text: String, from: Language, to: Language): String {
        val sess = session ?: throw IllegalStateException("Translation model not loaded")
        if (srcTokenToId.isEmpty() || tgtIdToToken.isEmpty()) throw IllegalStateException("Translation vocab not loaded")
        val srcTag = langToSrcTag[from] ?: "eng_Latn"
        val tgtTag = langToSrcTag[to] ?: "hin_Deva"
        val prompt = "$srcTag $tgtTag $text"
        val inputIds = tokenizeSrc(prompt)
        if (inputIds.isEmpty()) return text
        val attentionMask = LongArray(inputIds.size) { 1L }
        var decoderIds = longArrayOf(PAD_ID.toLong())
        val env = OrtHelper.environment
        val tokenIds = mutableListOf<Int>()
        var shouldStop = false
        for (_step in 0 until MAX_DECODE_LENGTH) {
            if (shouldStop) break
            val decoderInput = OrtHelper.createLongTensor(env, decoderIds, longArrayOf(1, decoderIds.size.toLong()))
            decoderInput.use {
                val inputIdsTensor = OrtHelper.createLongTensor(env, inputIds, longArrayOf(1, inputIds.size.toLong()))
                val attnTensor = OrtHelper.createLongTensor(env, attentionMask, longArrayOf(1, attentionMask.size.toLong()))
                inputIdsTensor.use {
                    attnTensor.use {
                        val inputs = mapOf(
                            "input_ids" to inputIdsTensor,
                            "attention_mask" to attnTensor,
                            "decoder_input_ids" to decoderInput
                        )
                        val result = sess.run(inputs)
                        val logitsTensor = result.get(sess.outputNames.first()) as OnnxTensor
                        result.close()
                        logitsTensor.use {
                            val shape = logitsTensor.info.shape
                            val seqLen = shape[1].toInt()
                            val vocabSize = shape[2].toInt()
                            val offset = (seqLen - 1) * vocabSize
                            var maxIdx = 0
                            var maxVal = Float.NEGATIVE_INFINITY
                            val buf = logitsTensor.floatBuffer
                            buf.position(offset)
                            for (j in 0 until vocabSize) {
                                val v = buf.get()
                                if (v > maxVal) {
                                    maxVal = v
                                    maxIdx = j
                                }
                            }
                            if (maxIdx == EOS_ID) {
                                shouldStop = true
                            } else {
                                tokenIds.add(maxIdx)
                                decoderIds = decoderIds + maxIdx.toLong()
                            }
                        }
                    }
                }
            }
        }
        return decodeTgt(tokenIds)
    }

    /** Greedy longest-match tokenization using SRC vocab. */
    private fun tokenizeSrc(text: String): LongArray {
        val ids = mutableListOf<Int>()
        var i = 0
        val trimmed = text.trim()
        while (i < trimmed.length) {
            var found = false
            for (len in (trimmed.length - i).downTo(1)) {
                val sub = trimmed.substring(i, i + len)
                val withSpace = if (ids.isEmpty()) "▁$sub" else sub
                val id = srcTokenToId[sub] ?: srcTokenToId[withSpace]
                if (id != null) {
                    ids.add(id)
                    i += len
                    found = true
                    break
                }
            }
            if (!found) {
                val id = srcTokenToId["<unk>"] ?: 3
                ids.add(id)
                i++
            }
        }
        return ids.map { it.toLong() }.toLongArray()
    }

    private fun decodeTgt(ids: List<Int>): String {
        return ids.map { tgtIdToToken[it] ?: "" }.joinToString("") { t ->
            if (t.startsWith("▁")) " " + t.drop(1) else t
        }.trim()
    }
}
