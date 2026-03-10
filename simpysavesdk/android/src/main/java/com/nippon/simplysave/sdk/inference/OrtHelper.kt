package com.nippon.simplysave.sdk.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ByteBuffer

/**
 * Shared ONNX Runtime environment. Sessions are created from model bytes.
 */
object OrtHelper {
    val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    fun createSession(modelBytes: ByteArray, options: OrtSession.SessionOptions? = null): OrtSession =
        if (options != null) environment.createSession(modelBytes, options)
        else environment.createSession(modelBytes)

    /** Create float tensor from flat data; shape e.g. [1, 80, T]. Uses direct buffer. */
    fun createFloatTensor(env: OrtEnvironment, data: FloatArray, shape: LongArray): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(data)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /** Create long tensor (e.g. token ids). */
    fun createLongTensor(env: OrtEnvironment, data: LongArray, shape: LongArray): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(data.size * 8).order(ByteOrder.nativeOrder()).asLongBuffer()
        buffer.put(data)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }
}
