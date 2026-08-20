package com.pepotech.pepoboveda.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Envuelve la clave maestra con una clave AES del Android Keystore que exige
 * autenticación biométrica fuerte (Clase 3) en cada uso.
 */
class BiometricKeyStore(private val archivoBlob: File) {

    companion object {
        private const val ALIAS = "pepo_boveda_bio_v1"
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val TAM_IV = 12
    }

    class BiometriaInvalidadaException : Exception("La biometría del dispositivo cambió")

    val estaConfigurada: Boolean get() = archivoBlob.exists() && claveExiste()

    private fun keystore(): KeyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }

    private fun claveExiste(): Boolean = try {
        keystore().containsAlias(ALIAS)
    } catch (e: Exception) {
        false
    }

    private fun obtenerClave(): SecretKey? = try {
        keystore().getKey(ALIAS, null) as? SecretKey
    } catch (e: Exception) {
        null
    }

    fun eliminar() {
        try {
            keystore().deleteEntry(ALIAS)
        } catch (e: Exception) {
            // sin clave que borrar
        }
        if (archivoBlob.exists()) archivoBlob.delete()
    }

    private fun crearClave(): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        val constructor = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            constructor.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            constructor.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generador.init(constructor.build())
        return generador.generateKey()
    }

    /** Cipher listo para envolver (requiere autenticación biométrica antes de usarse). */
    fun cipherParaEnvolver(): Cipher {
        eliminar()
        val clave = crearClave()
        return Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave) }
    }

    /** Cipher listo para desenvolver el blob existente. */
    fun cipherParaDesenvolver(): Cipher {
        val clave = obtenerClave() ?: throw BiometriaInvalidadaException()
        val blob = archivoBlob.readBytes()
        if (blob.size <= TAM_IV) throw BiometriaInvalidadaException()
        val iv = blob.copyOfRange(0, TAM_IV)
        return try {
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            eliminar()
            throw BiometriaInvalidadaException()
        }
    }

    /** Guarda la clave maestra envuelta con un cipher ya autenticado. */
    fun guardarEnvuelta(cipher: Cipher, claveMaestra: ByteArray) {
        val envuelta = cipher.doFinal(claveMaestra)
        VaultCrypto.escribirAtomico(archivoBlob, cipher.iv + envuelta)
    }

    /** Recupera la clave maestra con un cipher ya autenticado. */
    fun leerEnvuelta(cipher: Cipher): ByteArray {
        val blob = archivoBlob.readBytes()
        val cuerpo = blob.copyOfRange(TAM_IV, blob.size)
        return cipher.doFinal(cuerpo)
    }
}
