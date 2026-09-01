package com.pepotech.pepoboveda

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.crypto.BiometricKeyStore.FalloKeystore
import com.pepotech.pepoboveda.crypto.BiometricKeyStore.Modo
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException

/**
 * Corre en un móvil o emulador de verdad porque el Android Keystore no existe en la JVM.
 * Usa alias con prefijo "prueba_" para no tocar las claves reales de la app.
 */
@RunWith(AndroidJUnit4::class)
class BiometricKeyStoreTest {

    private lateinit var directorio: File
    private lateinit var almacen: BiometricKeyStore

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        directorio = File(contexto.filesDir, "prueba-keystore").apply { mkdirs() }
        almacen = BiometricKeyStore(directorio, prefijoAlias = "prueba_")
        almacen.eliminarTodo()
    }

    @After
    fun limpiar() {
        almacen.eliminarTodo()
        directorio.deleteRecursively()
    }

    @Test
    fun modo_compatible_envuelve_y_desenvuelve_sin_huella() {
        val clave = ByteArray(32) { it.toByte() }
        almacen.envolver(Modo.COMPATIBLE, clave, null)
        assertTrue(almacen.estaConfigurada(Modo.COMPATIBLE))
        assertFalse(almacen.estaConfigurada(Modo.FUERTE))
        assertEquals(Modo.COMPATIBLE, almacen.modoConfigurado())
        assertArrayEquals(clave, almacen.desenvolver(Modo.COMPATIBLE, null))
    }

    @Test
    fun eliminar_un_modo_no_toca_el_otro() {
        almacen.envolver(Modo.COMPATIBLE, ByteArray(32), null)
        almacen.eliminar(Modo.FUERTE)
        assertTrue(almacen.estaConfigurada(Modo.COMPATIBLE))
        almacen.eliminar(Modo.COMPATIBLE)
        assertNull(almacen.modoConfigurado())
        try {
            almacen.desenvolver(Modo.COMPATIBLE, null)
            fail("Sin clave tendría que fallar")
        } catch (e: Exception) {
            // esperado: sin blob ni clave no hay nada que desenvolver
        }
    }

    @Test
    fun clasificar_distingue_los_fallos_del_keystore() {
        assertEquals(FalloKeystore.Invalidada, BiometricKeyStore.clasificar(KeyPermanentlyInvalidatedException()))
        assertEquals(FalloKeystore.Invalidada, BiometricKeyStore.clasificar(BiometricKeyStore.BiometriaInvalidadaException()))
        assertEquals(FalloKeystore.NoAutenticada, BiometricKeyStore.clasificar(UserNotAuthenticatedException()))
        // El -26 real llega envuelto: doFinal lanza IllegalBlockSizeException con la causa dentro.
        val envuelta = IllegalBlockSizeException().apply { initCause(UserNotAuthenticatedException("Key user not authenticated")) }
        assertEquals(FalloKeystore.NoAutenticada, BiometricKeyStore.clasificar(envuelta))
        val otro = BiometricKeyStore.clasificar(AEADBadTagException("tag mismatch"))
        assertTrue(otro is FalloKeystore.Otro)
        assertTrue((otro as FalloKeystore.Otro).detalle.contains("AEADBadTagException"))
    }
}
