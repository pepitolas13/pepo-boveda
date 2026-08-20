package com.pepotech.pepoboveda

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pepotech.pepoboveda.crypto.Argon2Kdf
import com.pepotech.pepoboveda.crypto.KdfParams
import com.pepotech.pepoboveda.crypto.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Argon2Kt es JNI: solo corre en dispositivo o emulador, no en la JVM de
 * escritorio. Aquí comprobamos el KDF de verdad con salt fijo.
 */
@RunWith(AndroidJUnit4::class)
class Argon2Test {

    private val salt = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun argon2id_es_determinista_con_salt_fijo() {
        val a = Argon2Kdf.derivar("pepotech-2026".toCharArray(), salt, KdfParams.PREDETERMINADOS)
        val b = Argon2Kdf.derivar("pepotech-2026".toCharArray(), salt, KdfParams.PREDETERMINADOS)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun argon2id_cambia_con_salt_o_contrasena() {
        val base = Argon2Kdf.derivar("pepotech-2026".toCharArray(), salt, KdfParams.PREDETERMINADOS)
        val otroSalt = Argon2Kdf.derivar("pepotech-2026".toCharArray(), ByteArray(16), KdfParams.PREDETERMINADOS)
        val otraClave = Argon2Kdf.derivar("otra cosa".toCharArray(), salt, KdfParams.PREDETERMINADOS)
        assertFalse(base.contentEquals(otroSalt))
        assertFalse(base.contentEquals(otraClave))
    }

    @Test
    fun los_parametros_por_defecto_son_los_del_modelo_de_seguridad() {
        val p = KdfParams.PREDETERMINADOS
        assertEquals(65_536, p.memoryKiB)
        assertEquals(3, p.iterations)
        assertEquals(4, p.parallelism)
        assertEquals(32, p.hashLength)
    }

    @Test
    fun la_boveda_va_y_viene_con_argon2_real() {
        val plano = "{\"version\":1,\"entradas\":[]}".toByteArray()
        val clave = Argon2Kdf.derivar("clave maestra".toCharArray(), salt, KdfParams.PREDETERMINADOS)
        val archivo = VaultCrypto.cifrar(plano, clave, salt, KdfParams.PREDETERMINADOS)
        assertArrayEquals(plano, VaultCrypto.descifrar(archivo, clave))
    }
}
