package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.util.Dominios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DominiosTest {

    @Test
    fun `extrae el host de una url completa`() {
        assertEquals("ejemplo.com", Dominios.host("https://www.ejemplo.com/entrar?x=1#a"))
        assertEquals("ejemplo.com", Dominios.host("EJEMPLO.COM"))
        assertEquals("ejemplo.com", Dominios.host("https://usuario:clave@ejemplo.com:8443/ruta"))
    }

    @Test
    fun `calcula el dominio raiz`() {
        assertEquals("ejemplo.com", Dominios.raiz("https://cuentas.ejemplo.com/login"))
        assertEquals("ejemplo.co.uk", Dominios.raiz("https://login.ejemplo.co.uk"))
        assertEquals("ejemplo.com.ar", Dominios.raiz("tienda.ejemplo.com.ar"))
    }

    @Test
    fun `coincide entre subdominios del mismo sitio`() {
        assertTrue(Dominios.coincide("ejemplo.com", "cuentas.ejemplo.com"))
        assertTrue(Dominios.coincide("https://ejemplo.com/entrar", "ejemplo.com"))
    }

    @Test
    fun `no coincide con dominios que solo se parecen`() {
        assertFalse(Dominios.coincide("ejemplo.com", "ejemplo.com.evil.net"))
        assertFalse(Dominios.coincide("ejemplo.com", "ejemplo-com.net"))
        assertFalse(Dominios.coincide("ejemplo.com", "miejemplo.com"))
        assertFalse(Dominios.coincide("banco.es", "banco.es.phishing.com"))
        assertFalse(Dominios.coincide("", "ejemplo.com"))
        assertFalse(Dominios.coincide("ejemplo.com", ""))
    }

    @Test
    fun `deriva un dominio desde el paquete de la app`() {
        assertEquals("ejemplo.com", Dominios.dominioDePaquete("com.ejemplo.android"))
        assertEquals("banco.es", Dominios.dominioDePaquete("es.banco"))
    }
}
