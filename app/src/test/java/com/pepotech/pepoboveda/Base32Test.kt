package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.crypto.Base32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Base32Test {

    @Test
    fun `vectores del RFC 4648`() {
        assertEquals("MY", Base32.codificar("f".toByteArray()))
        assertEquals("MZXQ", Base32.codificar("fo".toByteArray()))
        assertEquals("MZXW6", Base32.codificar("foo".toByteArray()))
        assertEquals("MZXW6YQ", Base32.codificar("foob".toByteArray()))
        assertEquals("MZXW6YTB", Base32.codificar("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", Base32.codificar("foobar".toByteArray()))
    }

    @Test
    fun `decodifica los vectores del RFC 4648`() {
        assertArrayEquals("foobar".toByteArray(), Base32.decodificar("MZXW6YTBOI"))
        assertArrayEquals("Hello".toByteArray(), Base32.decodificar("JBSWY3DP"))
    }

    @Test
    fun `tolera espacios guiones minusculas y relleno`() {
        assertArrayEquals("foobar".toByteArray(), Base32.decodificar("mzxw 6ytb-oi"))
        assertArrayEquals("fooba".toByteArray(), Base32.decodificar("MZXW6YTB======"))
    }

    @Test
    fun `ida y vuelta con bytes arbitrarios`() {
        val datos = ByteArray(64) { (it * 7 + 3).toByte() }
        assertArrayEquals(datos, Base32.decodificar(Base32.codificar(datos)))
    }

    @Test
    fun `rechaza caracteres invalidos`() {
        assertFalse(Base32.esValido("MZXW6YTB1"))
        assertFalse(Base32.esValido(""))
        assertTrue(Base32.esValido("MZXW6YTBOI"))
    }
}
