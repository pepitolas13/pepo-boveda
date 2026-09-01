package com.pepotech.pepoboveda

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Biometria.Capacidad
import com.pepotech.pepoboveda.util.Biometria.Nivel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometriaNivelTest {

    private val ok = BiometricManager.BIOMETRIC_SUCCESS
    private val sinHuella = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    private val sinHardware = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    private val ocupado = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    private val noSoportado = BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
    private val desconocido = BiometricManager.BIOMETRIC_STATUS_UNKNOWN

    @Test
    fun `con Clase 3 el nivel es fuerte`() {
        assertEquals(Nivel.FUERTE, Biometria.decidirNivel(Capacidad(ok, ok, ok, ok)))
        assertEquals(Nivel.FUERTE, Biometria.decidirNivel(Capacidad(ok, sinHuella, sinHardware, sinHuella)))
    }

    @Test
    fun `si Android no sabe decirlo se intenta el fuerte`() {
        assertEquals(Nivel.FUERTE, Biometria.decidirNivel(Capacidad(desconocido, desconocido, desconocido, desconocido)))
    }

    @Test
    fun `huella de Clase 2 o PIN dan el compatible (caso ROM personalizada)`() {
        assertEquals(Nivel.COMPATIBLE, Biometria.decidirNivel(Capacidad(sinHuella, ok, ok, ok)))
        assertEquals(Nivel.COMPATIBLE, Biometria.decidirNivel(Capacidad(noSoportado, ok, noSoportado, ok)))
        assertEquals(Nivel.COMPATIBLE, Biometria.decidirNivel(Capacidad(sinHardware, sinHardware, ok, ok)))
        // Solo credencial: API 29 no sabe responder por DEVICE_CREDENTIAL a solas, pero sí por la combinación.
        assertEquals(Nivel.COMPATIBLE, Biometria.decidirNivel(Capacidad(sinHardware, sinHardware, noSoportado, ok)))
    }

    @Test
    fun `sin nada utilizable el nivel es ninguno`() {
        assertEquals(Nivel.NINGUNO, Biometria.decidirNivel(Capacidad(sinHardware, sinHardware, sinHuella, sinHuella)))
        assertEquals(Nivel.NINGUNO, Biometria.decidirNivel(Capacidad(ocupado, ocupado, ocupado, ocupado)))
    }

    @Test
    fun `todos los codigos tienen explicacion`() {
        listOf(
            ok, sinHuella, sinHardware, ocupado, noSoportado, desconocido,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, 12345
        ).forEach {
            assertTrue(Biometria.explicar(it).isNotBlank())
        }
    }

    @Test
    fun `la falta de fuerte se explica segun el caso`() {
        assertTrue(Biometria.explicarFaltaDeFuerte(Capacidad(sinHuella, ok, ok, ok)).contains("Clase 2"))
        assertTrue(Biometria.explicarFaltaDeFuerte(Capacidad(sinHuella, sinHuella, ok, ok)).contains("ninguna huella"))
        assertTrue(Biometria.explicarFaltaDeFuerte(Capacidad(sinHardware, sinHardware, ok, ok)).contains("Clase 3"))
    }

    @Test
    fun `los errores del prompt se clasifican`() {
        assertEquals(Biometria.Fallo.Cancelado, Biometria.clasificarError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, ""))
        assertEquals(Biometria.Fallo.Cancelado, Biometria.clasificarError(BiometricPrompt.ERROR_USER_CANCELED, ""))
        assertNull(Biometria.Fallo.Cancelado.texto)
        assertEquals(Biometria.Fallo.Bloqueado(true), Biometria.clasificarError(BiometricPrompt.ERROR_LOCKOUT, ""))
        assertEquals(Biometria.Fallo.Bloqueado(false), Biometria.clasificarError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, ""))
        assertTrue(Biometria.clasificarError(BiometricPrompt.ERROR_HW_UNAVAILABLE, "") is Biometria.Fallo.NoDisponible)
        val otro = Biometria.clasificarError(BiometricPrompt.ERROR_VENDOR, "Sensor sucio")
        assertEquals("Sensor sucio", otro.texto)
        assertNotNull(Biometria.clasificarError(999, "").texto)
    }
}
