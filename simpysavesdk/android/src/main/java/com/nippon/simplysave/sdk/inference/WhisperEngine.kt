package com.nippon.simplysave.sdk.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import org.json.JSONObject

/**
 * STT via Whisper ONNX: encoder + decoder, greedy decode, vocab decode.
 * No fallback: requires encoder, decoder, and vocab bytes from model storage.
 */
object WhisperEngine {

    private const val START_OF_TRANSCRIPT = 50257
    private const val END_OF_TEXT = 50256
    private const val MAX_NEW_TOKENS = 224

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var idToToken: Map<Int, String> = emptyMap()

    fun load(encoderBytes: ByteArray?, decoderBytes: ByteArray?, vocabBytes: ByteArray?) {
        unload()
        if (encoderBytes == null || decoderBytes == null || encoderBytes.isEmpty() || decoderBytes.isEmpty()) return
        encoderSession = OrtHelper.createSession(encoderBytes)
        decoderSession = OrtHelper.createSession(decoderBytes)
        if (vocabBytes != null && vocabBytes.isNotEmpty()) {
            val vocab = JSONObject(String(vocabBytes, Charsets.UTF_8))
            val tokenToId = vocab.optJSONObject("model")?.optJSONObject("vocab") ?: vocab
            idToToken = mutableMapOf<Int, String>().apply {
                tokenToId.keys().forEach { token ->
                    put(tokenToId.getInt(token), token)
                }
            }
        }
    }

    fun unload() {
        encoderSession?.close()
        decoderSession?.close()
        encoderSession = null
        decoderSession = null
        idToToken = emptyMap()
    }

    fun transcribe(melSpectrogram: Array<FloatArray>, customVocabularyBias: List<String>): String {
        val enc = encoderSession ?: throw IllegalStateException("Whisper encoder not loaded")
        val dec = decoderSession ?: throw IllegalStateException("Whisper decoder not loaded")
        if (idToToken.isEmpty()) throw IllegalStateException("Whisper vocab not loaded")

        val nFrames = melSpectrogram.size
        val nMel = 80
        if (nFrames == 0) return ""

        // mel is [nFrames][nMel]; Whisper expects [1, nMel, nFrames]
        val flat = FloatArray(1 * nMel * nFrames)
        for (t in 0 until nFrames) {
            for (m in 0 until nMel) {
                flat[m * nFrames + t] = melSpectrogram[t].getOrElse(m) { 0f }
            }
        }

        val env = OrtHelper.environment
        val inputFeatures = OrtHelper.createFloatTensor(env, flat, longArrayOf(1, nMel.toLong(), nFrames.toLong()))
        inputFeatures.use {
            val encInputs = mapOf("input_features" to inputFeatures)
            val encResult = enc.run(encInputs)
            val outName = enc.outputNames.firstOrNull { it.contains("hidden") } ?: enc.outputNames.first()
            val lastHiddenState = encResult.get(outName) as OnnxTensor
            encResult.close()
            try {
                var decoderIds = longArrayOf(START_OF_TRANSCRIPT.toLong())
                val tokenIds = mutableListOf<Int>()

                for (_step in 0 until MAX_NEW_TOKENS) {
                    val decoderInput = OrtHelper.createLongTensor(env, decoderIds, longArrayOf(1, decoderIds.size.toLong()))
                    decoderInput.use {
                        val decInputNames = dec.inputNames
                        val decInputs = mutableMapOf<String, OnnxTensor>()
                        for (name in decInputNames) {
                            when {
                                name.contains("decoder_input_ids") || name == "input_ids" -> decInputs[name] = decoderInput
                                name.contains("encoder_hidden_states") || name.contains("encoder_output") -> decInputs[name] = lastHiddenState
                            }
                        }
                        if (decInputs.isEmpty()) {
                            decInputs["decoder_input_ids"] = decoderInput
                            decInputs["encoder_hidden_states"] = lastHiddenState
                        }
                        val decResult = dec.run(decInputs)
                        val logitsTensor = decResult.get(dec.outputNames.first()) as OnnxTensor
                        decResult.close()
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
                            val nextId = maxIdx
                            if (nextId == END_OF_TEXT) return decodeTokens(tokenIds)
                            tokenIds.add(nextId)
                            decoderIds = decoderIds + nextId.toLong()
                        }
                    }
                }
                return decodeTokens(tokenIds)
            } finally {
                lastHiddenState.close()
            }
        }
    }

    private fun decodeTokens(ids: List<Int>): String {
        val parts = ids.map { idToToken[it] ?: "" }.filter { it.isNotEmpty() }
        return parts.joinToString("") { t -> if (t == "Ġ" || t.startsWith("Ġ")) " " + t.drop(1) else t }.trim()
    }
}
