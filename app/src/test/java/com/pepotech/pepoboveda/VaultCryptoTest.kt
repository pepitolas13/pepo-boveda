package com.pepotech.pepoboveda

import com.pepotech.pepoboveda.crypto.ContrasenaIncorrectaException
import com.pepotech.pepoboveda.crypto.FormatoInvalidoException
import com.pepotech.pepoboveda.crypto.Kdf
import com.pepotech.pepoboveda.crypto.KdfParams
import com.pepotech.pepoboveda.crypto.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** KDF de mentira: Argon2 usa JNI y no corre en la JVM de escritorio. */
private object KdfDePrueba : Kdf {
    override fun derivar(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(String(password).toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(params.memoryKiB.toString().toByteArray())
        return digest.digest().copyOf(params.hashLength)
    }
}

class VaultCryptoTest {

    private val plano = "{\"version\":1,\"entradas\":[]}".toByteArray(Charsets.UTF_8)

    private fun clave(texto: String, salt: ByteArray) =
        VaultCrypto.derivarClave(texto.toCharArray(), salt, KdfParams.PREDETERMINADOS, KdfDePrueba)

    @Test
    fun `cifrar y descifrar devuelve el mismo contenido`() {
        val salt = VaultCrypto.nuevoSalt()
        val k = clave("contraseña maestra", salt)
        val archivo = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        assertArrayEquals(plano, VaultCrypto.descifrar(archivo, k))
    }

    @Test
    fun `el archivo empieza por el magic BVDA`() {
        val salt = VaultCrypto.nuevoSalt()
        val archivo = VaultCrypto.cifrar(plano, clave("x", salt), salt, KdfParams.PREDETERMINADOS)
        assertEquals("BVDA", String(archivo.copyOf(4), Charsets.US_ASCII))
    }

    @Test
    fun `la cabecera conserva salt y parametros`() {
        val salt = VaultCrypto.nuevoSalt()
        val params = KdfParams(memoryKiB = 32_768, iterations = 4, parallelism = 2, hashLength = 32)
        val archivo = VaultCrypto.cifrar(plano, clave("x", salt), salt, params)
        val cabecera = VaultCrypto.leerCabecera(archivo)
        assertArrayEquals(salt, cabecera.salt)
        assertEquals(params, cabecera.params)
        assertEquals(VaultCrypto.VERSION, cabecera.version)
    }

    @Test(expected = ContrasenaIncorrectaException::class)
    fun `una contrasena distinta no descifra`() {
        val salt = VaultCrypto.nuevoSalt()
        val archivo = VaultCrypto.cifrar(plano, clave("buena", salt), salt, KdfParams.PREDETERMINADOS)
        VaultCrypto.descifrar(archivo, clave("mala", salt))
    }

    @Test(expected = ContrasenaIncorrectaException::class)
    fun `tocar el cuerpo cifrado invalida el archivo`() {
        val salt = VaultCrypto.nuevoSalt()
        val k = clave("x", salt)
        val archivo = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        archivo[archivo.size - 1] = (archivo[archivo.size - 1] + 1).toByte()
        VaultCrypto.descifrar(archivo, k)
    }

    @Test(expected = ContrasenaIncorrectaException::class)
    fun `tocar el nonce de la cabecera invalida el archivo`() {
        val salt = VaultCrypto.nuevoSalt()
        val k = clave("x", salt)
        val archivo = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        val posicionNonce = VaultCrypto.TAM_CABECERA - 1
        archivo[posicionNonce] = (archivo[posicionNonce] + 1).toByte()
        VaultCrypto.descifrar(archivo, k)
    }

    @Test(expected = FormatoInvalidoException::class)
    fun `un archivo con otro magic se rechaza`() {
        val salt = VaultCrypto.nuevoSalt()
        val archivo = VaultCrypto.cifrar(plano, clave("x", salt), salt, KdfParams.PREDETERMINADOS)
        archivo[0] = 'X'.code.toByte()
        VaultCrypto.leerCabecera(archivo)
    }

    @Test(expected = FormatoInvalidoException::class)
    fun `un archivo demasiado corto se rechaza`() {
        VaultCrypto.leerCabecera(ByteArray(10))
    }

    @Test
    fun `dos cifrados de lo mismo usan nonces distintos`() {
        val salt = VaultCrypto.nuevoSalt()
        val k = clave("x", salt)
        val a = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        val b = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        assertNotEquals(
            VaultCrypto.leerCabecera(a).nonce.toList(),
            VaultCrypto.leerCabecera(b).nonce.toList()
        )
    }

    @Test
    fun `la escritura atomica no deja archivo temporal`() {
        val carpeta = File(System.getProperty("java.io.tmpdir"), "pepo-boveda-test-" + System.nanoTime())
        carpeta.mkdirs()
        val destino = File(carpeta, "boveda.bvda")
        val salt = VaultCrypto.nuevoSalt()
        val archivo = VaultCrypto.cifrar(plano, clave("x", salt), salt, KdfParams.PREDETERMINADOS)
        VaultCrypto.escribirAtomico(destino, archivo)
        assertTrue(destino.exists())
        assertArrayEquals(archivo, destino.readBytes())
        assertFalse(File(carpeta, "boveda.bvda.tmp").exists())
        VaultCrypto.escribirAtomico(destino, archivo)
        assertEquals(1, carpeta.listFiles()?.size)
        carpeta.deleteRecursively()
    }

    @Test
    fun `la escritura interrumpida no corrompe la boveda anterior`() {
        val carpeta = File(System.getProperty("java.io.tmpdir"), "pepo-boveda-crash-" + System.nanoTime())
        carpeta.mkdirs()
        val destino = File(carpeta, "boveda.bvda")
        val salt = VaultCrypto.nuevoSalt()
        val k = clave("x", salt)
        val bueno = VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS)
        VaultCrypto.escribirAtomico(destino, bueno)
        // Simula un corte de corriente: el .tmp quedó a medias y nunca se renombró.
        File(carpeta, "boveda.bvda.tmp").writeBytes(byteArrayOf(1, 2, 3))
        assertArrayEquals(bueno, destino.readBytes())
        assertArrayEquals(plano, VaultCrypto.descifrar(destino.readBytes(), k))
        // La siguiente escritura buena pisa el temporal sin dejar basura.
        VaultCrypto.escribirAtomico(destino, VaultCrypto.cifrar(plano, k, salt, KdfParams.PREDETERMINADOS))
        assertEquals(1, carpeta.listFiles()?.size)
        assertArrayEquals(plano, VaultCrypto.descifrar(destino.readBytes(), k))
        carpeta.deleteRecursively()
    }

    /** Vector propio con salt fijo: el KDF tiene que ser determinista. */
    @Test
    fun `vector determinista con salt fijo`() {
        val salt = ByteArray(16) { (it + 1).toByte() }
        val derivada = VaultCrypto.derivarClave(
            "pepotech-2026".toCharArray(),
            salt,
            KdfParams.PREDETERMINADOS,
            KdfDePrueba
        )
        assertEquals(
            "3fe1674025889e8d4967f5c487d7b6dbc4707cac1876fb91980724ee73ada7d1",
            derivada.joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun `el kdf mezcla salt y contrasena`() {
        val salt1 = VaultCrypto.nuevoSalt()
        val salt2 = VaultCrypto.nuevoSalt()
        assertFalse(clave("x", salt1).contentEquals(clave("x", salt2)))
        assertFalse(clave("x", salt1).contentEquals(clave("y", salt1)))
        assertArrayEquals(clave("x", salt1), clave("x", salt1))
        assertEquals(32, clave("x", salt1).size)
    }
}
