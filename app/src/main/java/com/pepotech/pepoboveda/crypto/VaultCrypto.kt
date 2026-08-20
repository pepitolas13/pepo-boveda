package com.pepotech.pepoboveda.crypto

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FormatoInvalidoException(mensaje: String) : Exception(mensaje)

class ContrasenaIncorrectaException : Exception("La contraseña no es correcta o el archivo está dañado")

object VaultCrypto {

    val MAGIC = byteArrayOf('B'.code.toByte(), 'V'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte())
    const val VERSION = 1
    const val TAM_SALT = 16
    const val TAM_NONCE = 12
    const val BITS_TAG = 128
    const val TAM_CABECERA = 4 + 1 + TAM_SALT + 16 + TAM_NONCE

    private val aleatorio = SecureRandom()

    data class Cabecera(
        val version: Int,
        val salt: ByteArray,
        val params: KdfParams,
        val nonce: ByteArray
    )

    fun aleatorios(n: Int): ByteArray = ByteArray(n).also { aleatorio.nextBytes(it) }

    fun nuevoSalt(): ByteArray = aleatorios(TAM_SALT)

    fun derivarClave(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams = KdfParams.PREDETERMINADOS,
        kdf: Kdf = Argon2Kdf
    ): ByteArray = kdf.derivar(password, salt, params)

    private fun cabeceraABytes(salt: ByteArray, params: KdfParams, nonce: ByteArray): ByteArray {
        require(salt.size == TAM_SALT) { "salt inválido" }
        require(nonce.size == TAM_NONCE) { "nonce inválido" }
        val buffer = ByteBuffer.allocate(TAM_CABECERA)
        buffer.put(MAGIC)
        buffer.put(VERSION.toByte())
        buffer.put(salt)
        buffer.putInt(params.memoryKiB)
        buffer.putInt(params.iterations)
        buffer.putInt(params.parallelism)
        buffer.putInt(params.hashLength)
        buffer.put(nonce)
        return buffer.array()
    }

    fun leerCabecera(archivo: ByteArray): Cabecera {
        if (archivo.size < TAM_CABECERA + 16) throw FormatoInvalidoException("Archivo demasiado corto")
        val buffer = ByteBuffer.wrap(archivo, 0, TAM_CABECERA)
        val magic = ByteArray(4).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw FormatoInvalidoException("No es un archivo de bóveda")
        val version = buffer.get().toInt()
        if (version != VERSION) throw FormatoInvalidoException("Versión de bóveda no soportada: $version")
        val salt = ByteArray(TAM_SALT).also { buffer.get(it) }
        val memoria = buffer.int
        val iteraciones = buffer.int
        val paralelismo = buffer.int
        val longitud = buffer.int
        val nonce = ByteArray(TAM_NONCE).also { buffer.get(it) }
        if (memoria !in 1_024..1_048_576 || iteraciones !in 1..64 || paralelismo !in 1..64 || longitud != 32) {
            throw FormatoInvalidoException("Parámetros Argon2 fuera de rango")
        }
        return Cabecera(version, salt, KdfParams(memoria, iteraciones, paralelismo, longitud), nonce)
    }

    fun cifrar(plano: ByteArray, clave: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        val nonce = aleatorios(TAM_NONCE)
        val cabecera = cabeceraABytes(salt, params, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(BITS_TAG, nonce))
        cipher.updateAAD(cabecera)
        val cifrado = cipher.doFinal(plano)
        return cabecera + cifrado
    }

    fun descifrar(archivo: ByteArray, clave: ByteArray): ByteArray {
        val cabecera = leerCabecera(archivo)
        val bytesCabecera = archivo.copyOfRange(0, TAM_CABECERA)
        val cuerpo = archivo.copyOfRange(TAM_CABECERA, archivo.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(BITS_TAG, cabecera.nonce))
        cipher.updateAAD(bytesCabecera)
        try {
            return cipher.doFinal(cuerpo)
        } catch (e: AEADBadTagException) {
            throw ContrasenaIncorrectaException()
        } catch (e: javax.crypto.BadPaddingException) {
            throw ContrasenaIncorrectaException()
        }
    }

    fun escribirAtomico(destino: File, contenido: ByteArray) {
        destino.parentFile?.mkdirs()
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        FileOutputStream(temporal).use { salida ->
            salida.write(contenido)
            salida.flush()
            salida.fd.sync()
        }
        if (!temporal.renameTo(destino)) {
            if (destino.exists() && !destino.delete()) {
                temporal.delete()
                throw java.io.IOException("No se pudo reemplazar la bóveda")
            }
            if (!temporal.renameTo(destino)) {
                temporal.delete()
                throw java.io.IOException("No se pudo renombrar la bóveda temporal")
            }
        }
    }
}
