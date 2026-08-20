package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.crypto.Base32
import com.pepotech.pepoboveda.crypto.Totp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {

    private val secreto = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    /** Vectores del RFC 6238, apéndice B (HMAC-SHA1, 8 dígitos). */
    @Test
    fun `vectores oficiales del RFC 6238`() {
        assertEquals("94287082", Totp.codigo(secreto, 59L, digitos = 8))
        assertEquals("07081804", Totp.codigo(secreto, 1_111_111_109L, digitos = 8))
        assertEquals("14050471", Totp.codigo(secreto, 1_111_111_111L, digitos = 8))
        assertEquals("89005924", Totp.codigo(secreto, 1_234_567_890L, digitos = 8))
        assertEquals("69279037", Totp.codigo(secreto, 2_000_000_000L, digitos = 8))
        assertEquals("65353130", Totp.codigo(secreto, 20_000_000_000L, digitos = 8))
    }

    @Test
    fun `el codigo de seis digitos es el sufijo del de ocho`() {
        val ocho = Totp.codigo(secreto, 59L, digitos = 8)
        val seis = Totp.codigo(secreto, 59L, digitos = 6)
        assertEquals(6, seis.length)
        assertEquals(ocho.takeLast(6), seis)
    }

    @Test
    fun `el codigo no cambia dentro del mismo periodo`() {
        assertEquals(Totp.codigo(secreto, 60L), Totp.codigo(secreto, 89L))
        assertTrue(Totp.codigo(secreto, 60L) != Totp.codigo(secreto, 90L))
    }

    @Test
    fun `los segundos restantes van de 30 a 1`() {
        assertEquals(30L, Totp.segundosRestantes(60L))
        assertEquals(1L, Totp.segundosRestantes(89L))
        assertEquals(30L, Totp.segundosRestantes(90L))
    }

    @Test
    fun `acepta secretos en base32`() {
        val base32 = Base32.codificar(secreto)
        assertEquals(Totp.codigo(secreto, 59L), Totp.codigoDesdeBase32(base32, 59L))
    }
}
