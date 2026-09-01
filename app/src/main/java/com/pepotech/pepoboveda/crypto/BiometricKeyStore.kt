package com.pepotech.pepoboveda.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.pepotech.pepoboveda.util.Diagnostico
import java.io.File
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Envuelve la clave maestra con una clave AES del Android Keystore. Hay dos modos:
 *
 * - [Modo.FUERTE]: la clave exige autenticación biométrica de Clase 3 en cada uso y se
 *   invalida si cambia la biometría del móvil. Es el modo de siempre y el preferido.
 * - [Modo.COMPATIBLE]: la clave vive en el Keystore (no sale del móvil, solo esta app
 *   puede usarla) pero NO exige autenticación del chip. Quien comprueba la huella o el
 *   PIN es Android, y la app decide abrir. Para móviles y ROMs donde la Clase 3 no existe
 *   o el Keystore rechaza la operación aunque la huella sea correcta.
 *
 * Cada modo tiene su alias y su archivo: activar uno borra el otro. [prefijoAlias] solo lo
 * usan los tests, para no tocar las claves reales del usuario en el mismo Keystore.
 */
class BiometricKeyStore(private val directorio: File, private val prefijoAlias: String = "") {

    enum class Modo(val clave: String, val alias: String, val archivo: String, val etiqueta: String) {
        FUERTE("fuerte", "pepo_boveda_bio_v1", "bio.blob", "fuerte (Clase 3, atada al Keystore)"),
        COMPATIBLE("compatible", "pepo_boveda_bio_compat_v1", "bio_compat.blob", "compatible (comprobada por Android)");

        val otro: Modo get() = if (this == FUERTE) COMPATIBLE else FUERTE

        companion object {
            fun desde(clave: String?): Modo? = entries.firstOrNull { it.clave == clave }
        }
    }

    /** Por qué falló el Keystore, ya clasificado para que la pantalla sepa qué ofrecer. */
    sealed class FalloKeystore {
        /** La clave se invalidó (cambió la biometría o se borró): toca volver a activar. */
        object Invalidada : FalloKeystore()

        /**
         * Android dio la huella por buena pero el Keystore no recibió la autenticación
         * (error -26, KEY_USER_NOT_AUTHENTICATED). Típico de ROMs personalizadas: el modo
         * fuerte no va a funcionar en este móvil y hay que ofrecer el compatible.
         */
        object NoAutenticada : FalloKeystore()

        data class Otro(val detalle: String) : FalloKeystore()
    }

    class BiometriaInvalidadaException : Exception("La biometría del dispositivo cambió")

    companion object {
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val TAM_IV = 12

        /**
         * Recorre la excepción y sus causas y dice qué le pasó al Keystore. Pura: se prueba
         * sola. La distinción NoAutenticada/Otro solo cambia el texto del diagnóstico; lo
         * que decide el comportamiento es Invalidada frente a todo lo demás.
         */
        fun clasificar(e: Throwable): FalloKeystore {
            var actual: Throwable? = e
            var nivel = 0
            while (actual != null && nivel < 5) {
                if (actual is BiometriaInvalidadaException || actual is KeyPermanentlyInvalidatedException) {
                    return FalloKeystore.Invalidada
                }
                if (actual is UserNotAuthenticatedException) return FalloKeystore.NoAutenticada
                if (actual.javaClass.name == "android.security.KeyStoreException") {
                    val mensaje = actual.message.orEmpty()
                    if (mensaje.contains("not authenticated", ignoreCase = true) ||
                        Regex("(^|\\D)-26(\\D|$)").containsMatchIn(mensaje)
                    ) {
                        return FalloKeystore.NoAutenticada
                    }
                }
                val causa = actual.cause
                actual = if (causa === actual) null else causa
                nivel++
            }
            return FalloKeystore.Otro(Diagnostico.describir(e))
        }
    }

    private fun archivo(modo: Modo): File = File(directorio, prefijoAlias + modo.archivo)

    private fun alias(modo: Modo): String = prefijoAlias + modo.alias

    /** Un solo Keystore cargado por instancia: cargarlo es una llamada al servicio y no cambia. */
    private val almacen: KeyStore by lazy { KeyStore.getInstance(PROVEEDOR).apply { load(null) } }

    fun estaConfigurada(modo: Modo): Boolean = archivo(modo).exists() && claveExiste(modo)

    /** El modo que tiene clave y blob, o null si no hay ninguno. El fuerte manda si hubiera ambos. */
    fun modoConfigurado(): Modo? = Modo.entries.firstOrNull { estaConfigurada(it) }

    private fun claveExiste(modo: Modo): Boolean = try {
        almacen.containsAlias(alias(modo))
    } catch (e: Exception) {
        Diagnostico.apuntar("keystore", "No se pudo consultar el alias de ${modo.clave}", e)
        false
    }

    /**
     * Null solo si la clave ya no existe o no se puede recuperar (eso sí es invalidación).
     * Un fallo transitorio del servicio se propaga: no hay que borrar nada por él.
     */
    private fun obtenerClave(modo: Modo): SecretKey? = try {
        almacen.getKey(alias(modo), null) as? SecretKey
    } catch (e: UnrecoverableKeyException) {
        Diagnostico.apuntar("keystore", "La clave ${modo.clave} ya no se puede recuperar", e)
        null
    }

    fun eliminar(modo: Modo) {
        try {
            almacen.deleteEntry(alias(modo))
        } catch (e: Exception) {
            // sin clave que borrar
        }
        val f = archivo(modo)
        if (f.exists()) f.delete()
    }

    fun eliminarTodo() {
        Modo.entries.forEach { eliminar(it) }
    }

    private fun crearClave(modo: Modo): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        val constructor = KeyGenParameterSpec.Builder(
            alias(modo),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (modo == Modo.FUERTE) {
            constructor
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                constructor.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                constructor.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }
        generador.init(constructor.build())
        return generador.generateKey()
    }

    // ------------------------------------------- cifradores del modo fuerte (CryptoObject)

    /** Cipher listo para envolver en modo fuerte: la huella tiene que autenticarlo antes de usarse. */
    fun cipherParaEnvolver(): Cipher {
        eliminar(Modo.FUERTE)
        val clave = crearClave(Modo.FUERTE)
        return Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave) }
    }

    /** Cipher listo para desenvolver el blob fuerte existente. */
    fun cipherParaDesenvolver(): Cipher {
        val clave = obtenerClave(Modo.FUERTE) ?: throw BiometriaInvalidadaException()
        val blob = archivo(Modo.FUERTE).readBytes()
        if (blob.size <= TAM_IV) throw BiometriaInvalidadaException()
        val iv = blob.copyOfRange(0, TAM_IV)
        return try {
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            Diagnostico.apuntar("keystore", "Clave fuerte invalidada al iniciar el cipher", e)
            eliminar(Modo.FUERTE)
            throw BiometriaInvalidadaException()
        }
    }

    // ------------------------------------------------------ envolver y desenvolver

    /**
     * Guarda la clave maestra envuelta. En modo FUERTE, [cipher] es el que devolvió el
     * prompt ya autenticado; en COMPATIBLE se ignora y la clave se crea aquí, después de
     * que Android haya confirmado al usuario.
     */
    fun envolver(modo: Modo, claveMaestra: ByteArray, cipher: Cipher?) {
        val cifrador = when (modo) {
            Modo.FUERTE -> requireNotNull(cipher) { "El modo fuerte necesita el cipher autenticado" }
            Modo.COMPATIBLE -> {
                eliminar(Modo.COMPATIBLE)
                val clave = crearClave(Modo.COMPATIBLE)
                Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave) }
            }
        }
        val envuelta = cifrador.doFinal(claveMaestra)
        VaultCrypto.escribirAtomico(archivo(modo), cifrador.iv + envuelta)
    }

    /** Recupera la clave maestra. En FUERTE con el [cipher] autenticado; en COMPATIBLE con la clave del Keystore. */
    fun desenvolver(modo: Modo, cipher: Cipher?): ByteArray {
        val blob = archivo(modo).readBytes()
        if (blob.size <= TAM_IV) throw BiometriaInvalidadaException()
        val cifrador = when (modo) {
            Modo.FUERTE -> requireNotNull(cipher) { "El modo fuerte necesita el cipher autenticado" }
            Modo.COMPATIBLE -> {
                val clave = obtenerClave(Modo.COMPATIBLE) ?: throw BiometriaInvalidadaException()
                Cipher.getInstance(TRANSFORMACION).apply {
                    init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, blob.copyOfRange(0, TAM_IV)))
                }
            }
        }
        return cifrador.doFinal(blob.copyOfRange(TAM_IV, blob.size))
    }
}
