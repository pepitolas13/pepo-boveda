package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.util.Diagnostico
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticoTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun preparar(): File {
        val archivo = File(carpeta.root, "diagnostico.log")
        Diagnostico.iniciar(archivo)
        return archivo
    }

    @After
    fun limpiar() {
        Diagnostico.borrar()
        Diagnostico.esperarEscrituras()
    }

    @Test
    fun `apunta y devuelve las ultimas lineas en orden`() {
        preparar()
        Diagnostico.apuntar("camara", "uno")
        Diagnostico.apuntar("camara", "dos")
        Diagnostico.apuntar("huella", "tres", IllegalStateException("falló"))
        val ultimas = Diagnostico.ultimas(2)
        assertEquals(2, ultimas.size)
        assertTrue(ultimas[0].endsWith("camara: dos"))
        assertTrue(ultimas[1].contains("huella: tres [IllegalStateException: falló]"))
    }

    @Test
    fun `lo escrito sobrevive a un reinicio`() {
        val destino = preparar()
        Diagnostico.apuntar("camara", "antes de reiniciar")
        Diagnostico.esperarEscrituras()
        Diagnostico.iniciar(destino)
        assertTrue(Diagnostico.ultimas(5).any { it.endsWith("antes de reiniciar") })
    }

    @Test
    fun `el archivo se recorta al pasar del maximo`() {
        val destino = preparar()
        val relleno = "x".repeat(200)
        repeat(400) { Diagnostico.apuntar("camara", "$it $relleno") }
        Diagnostico.esperarEscrituras()
        assertTrue(destino.length() <= Diagnostico.MAX_BYTES_ARCHIVO)
        assertTrue(Diagnostico.ultimas(1).single().contains("399 "))
        assertEquals(Diagnostico.MAX_LINEAS_MEMORIA, Diagnostico.ultimas(10_000).size)
    }

    @Test
    fun `describir sigue las causas`() {
        val e = IllegalStateException("fuera", RuntimeException("dentro"))
        assertEquals("IllegalStateException: fuera <- RuntimeException: dentro", Diagnostico.describir(e))
    }

    @Test
    fun `el informe junta cabecera estado y pasos`() {
        preparar()
        Diagnostico.apuntar("camara", "paso")
        val informe = Diagnostico.informe("Móvil X", "Huella: ninguna")
        assertTrue(informe.startsWith("Móvil X"))
        assertTrue(informe.contains("Huella: ninguna"))
        assertTrue(informe.contains("camara: paso"))
    }
}
