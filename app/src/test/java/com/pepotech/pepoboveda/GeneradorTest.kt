package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.crypto.OpcionesGenerador
import com.pepotech.pepoboveda.crypto.PasswordGenerator
import com.pepotech.pepoboveda.crypto.Wordlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneradorTest {

    @Test
    fun `respeta la longitud pedida`() {
        listOf(8, 12, 20, 40, 64).forEach { largo ->
            val clave = PasswordGenerator.generar(OpcionesGenerador(longitud = largo))
            assertEquals(largo, clave.length)
        }
    }

    @Test
    fun `incluye al menos un caracter de cada grupo activo`() {
        repeat(50) {
            val clave = PasswordGenerator.generar(OpcionesGenerador(longitud = 20))
            assertTrue(clave.any { it in PasswordGenerator.MAYUSCULAS })
            assertTrue(clave.any { it in PasswordGenerator.MINUSCULAS })
            assertTrue(clave.any { it in PasswordGenerator.DIGITOS })
            assertTrue(clave.any { it in PasswordGenerator.SIMBOLOS })
        }
    }

    @Test
    fun `sin simbolos ni digitos solo salen letras`() {
        val opciones = OpcionesGenerador(longitud = 24, digitos = false, simbolos = false)
        repeat(20) {
            val clave = PasswordGenerator.generar(opciones)
            assertTrue(clave.all { it in PasswordGenerator.MAYUSCULAS || it in PasswordGenerator.MINUSCULAS })
        }
    }

    @Test
    fun `evita caracteres ambiguos`() {
        val ambiguos = listOf('I', 'O', '0', '1', 'l')
        val todos = PasswordGenerator.MAYUSCULAS + PasswordGenerator.MINUSCULAS + PasswordGenerator.DIGITOS
        ambiguos.forEach { assertTrue("$it no debería estar", it !in todos) }
    }

    @Test
    fun `dos contrasenas seguidas no coinciden`() {
        val a = PasswordGenerator.generar(OpcionesGenerador(longitud = 24))
        val b = PasswordGenerator.generar(OpcionesGenerador(longitud = 24))
        assertTrue(a != b)
    }

    @Test
    fun `la frase usa solo palabras de la lista`() {
        repeat(30) {
            val frase = PasswordGenerator.generar(OpcionesGenerador(modoFrase = true, palabras = 5))
            val partes = frase.split("-")
            assertEquals(5, partes.size)
            partes.forEach { assertTrue("$it no está en la lista", it in Wordlist.PALABRAS) }
        }
    }

    @Test
    fun `la lista de palabras es grande y sin repetidos`() {
        assertTrue("lista demasiado corta: ${Wordlist.TAMANO}", Wordlist.TAMANO >= 500)
        assertEquals(Wordlist.TAMANO, Wordlist.PALABRAS.distinct().size)
        assertTrue(Wordlist.PALABRAS.all { palabra -> palabra.all { it in 'a'..'z' } })
    }

    @Test
    fun `la entropia crece con la longitud`() {
        val corta = PasswordGenerator.entropiaBits(OpcionesGenerador(longitud = 8))
        val larga = PasswordGenerator.entropiaBits(OpcionesGenerador(longitud = 32))
        assertTrue(larga > corta)
        assertTrue(corta > 40.0)
    }

    @Test
    fun `el tiempo de crackeo se explica en castellano`() {
        assertEquals("menos de un segundo", PasswordGenerator.tiempoDeCrackeo(20.0))
        assertTrue(PasswordGenerator.tiempoDeCrackeo(128.0).isNotBlank())
        assertEquals("al instante", PasswordGenerator.tiempoDeCrackeo(0.0))
    }
}
