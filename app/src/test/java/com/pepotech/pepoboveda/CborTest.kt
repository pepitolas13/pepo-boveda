package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.passkey.Cbor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CborTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `enteros segun el RFC 8949`() {
        assertEquals("00", hex(Cbor.codificar(0)))
        assertEquals("17", hex(Cbor.codificar(23)))
        assertEquals("1818", hex(Cbor.codificar(24)))
        assertEquals("1903e8", hex(Cbor.codificar(1000)))
        assertEquals("20", hex(Cbor.codificar(-1)))
        assertEquals("26", hex(Cbor.codificar(-7)))
    }

    @Test
    fun `textos y bytes`() {
        assertEquals("6161", hex(Cbor.codificar("a")))
        assertEquals("6449455446", hex(Cbor.codificar("IETF")))
        assertEquals("4401020304", hex(Cbor.codificar(byteArrayOf(1, 2, 3, 4))))
    }

    @Test
    fun `mapas en orden de insercion`() {
        val mapa = LinkedHashMap<Any, Any>()
        mapa["fmt"] = "none"
        assertEquals("a163666d74646e6f6e65", hex(Cbor.codificar(mapa)))
    }

    @Test
    fun `la clave cose de es256 lleva los cinco campos`() {
        val x = ByteArray(32) { 1 }
        val y = ByteArray(32) { 2 }
        val cose = hex(Cbor.clavePublicaCose(x, y))
        assertTrue(cose.startsWith("a5"))
        assertTrue(cose.contains("010203262001"))
        assertTrue(cose.contains("5820" + "01".repeat(32)))
        assertTrue(cose.contains("5820" + "02".repeat(32)))
    }
}
