package com.pepotech.pepoboveda.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

object Zeroizar {

    fun borrar(bytes: ByteArray?) {
        if (bytes != null) bytes.fill(0)
    }

    fun borrar(chars: CharArray?) {
        if (chars != null) chars.fill('\u0000')
    }

    fun aBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) byteBuffer.array().fill(0)
        return bytes
    }
}
