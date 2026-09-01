package com.pepotech.pepoboveda.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Único sitio de la app que habla con BiometricPrompt. Distingue dos niveles:
 *
 * - FUERTE: huella de Clase 3 y clave del Keystore atada a ella (CryptoObject). Es lo
 *   que queremos siempre que el móvil lo dé.
 * - COMPATIBLE: huella de Clase 2 o PIN/patrón del móvil, comprobados por Android sin
 *   CryptoObject. Para móviles y ROMs donde la Clase 3 no existe o el Keystore la rechaza.
 *
 * Nada de aquí se cachea: la disponibilidad se pregunta cada vez, porque un sensor
 * ocupado por otra app o una huella recién registrada cambian la respuesta.
 */
object Biometria {

    /** Los cuatro códigos de canAuthenticate que importan, tal cual los da Android. */
    data class Capacidad(val fuerte: Int, val debil: Int, val credencial: Int, val compatible: Int)

    enum class Nivel { FUERTE, COMPATIBLE, NINGUNO }

    const val AUTENTICADORES_FUERTES = BIOMETRIC_STRONG
    const val AUTENTICADORES_COMPATIBLES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun capacidad(contexto: Context): Capacidad {
        val gestor = BiometricManager.from(contexto)
        return Capacidad(
            fuerte = gestor.canAuthenticate(AUTENTICADORES_FUERTES),
            debil = gestor.canAuthenticate(BIOMETRIC_WEAK),
            credencial = gestor.canAuthenticate(DEVICE_CREDENTIAL),
            compatible = gestor.canAuthenticate(AUTENTICADORES_COMPATIBLES)
        )
    }

    /** STATUS_UNKNOWN cuenta como "se intenta": algunas ROMs lo devuelven y luego funcionan. */
    fun hayFuerte(c: Capacidad): Boolean =
        c.fuerte == BiometricManager.BIOMETRIC_SUCCESS || c.fuerte == BiometricManager.BIOMETRIC_STATUS_UNKNOWN

    fun hayCompatible(c: Capacidad): Boolean =
        c.compatible == BiometricManager.BIOMETRIC_SUCCESS ||
            c.debil == BiometricManager.BIOMETRIC_SUCCESS ||
            c.credencial == BiometricManager.BIOMETRIC_SUCCESS

    /** El mejor nivel que ofrece el móvil ahora mismo. Función pura: se prueba en la JVM. */
    fun decidirNivel(c: Capacidad): Nivel = when {
        hayFuerte(c) -> Nivel.FUERTE
        hayCompatible(c) -> Nivel.COMPATIBLE
        else -> Nivel.NINGUNO
    }

    fun nivel(contexto: Context): Nivel = decidirNivel(capacidad(contexto))

    /** Qué significa un código de canAuthenticate, en cristiano. */
    fun explicar(codigo: Int): String = when (codigo) {
        BiometricManager.BIOMETRIC_SUCCESS -> "disponible"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "no hay ninguna huella registrada en Android"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "este móvil no tiene un sensor que Android reconozca"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "el sensor no responde ahora mismo"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Android pide una actualización de seguridad antes de dejar usarla"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "esta versión de Android no ofrece ese tipo de biometría"
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "Android no sabe decir si funciona; se probará igualmente"
        else -> "estado desconocido ($codigo)"
    }

    /** Por qué no hay huella fuerte en este móvil, para enseñarlo en Ajustes. */
    fun explicarFaltaDeFuerte(c: Capacidad): String = when (c.fuerte) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            if (c.debil == BiometricManager.BIOMETRIC_SUCCESS) {
                "Tu huella existe, pero Android la declara de Clase 2 (no vale para atar claves del Keystore)."
            } else {
                "No hay ninguna huella registrada en Android."
            }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
            "Este Android no ofrece huella de Clase 3."
        else -> "Huella de Clase 3: ${explicar(c.fuerte)}."
    }

    /** Resultado negativo de un prompt, ya clasificado para que la pantalla sepa qué decir. */
    sealed class Fallo {
        /** El usuario lo cerró: no hay nada que explicar. */
        object Cancelado : Fallo()
        data class Bloqueado(val temporal: Boolean) : Fallo()
        data class NoDisponible(val explicacion: String) : Fallo()
        data class Otro(val codigo: Int, val mensaje: String) : Fallo()

        /** Texto para el usuario, o null si no toca decir nada. */
        val texto: String?
            get() = when (this) {
                Cancelado -> null
                is Bloqueado -> if (temporal) {
                    "Demasiados intentos. Espera 30 segundos o usa la contraseña."
                } else {
                    "La huella queda bloqueada hasta que desbloquees el móvil con tu PIN o patrón."
                }
                is NoDisponible -> explicacion
                is Otro -> mensaje.ifBlank { "La huella no ha funcionado ($codigo)." }
            }
    }

    fun clasificarError(codigo: Int, mensaje: CharSequence): Fallo = when (codigo) {
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED -> Fallo.Cancelado
        BiometricPrompt.ERROR_LOCKOUT -> Fallo.Bloqueado(temporal = true)
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> Fallo.Bloqueado(temporal = false)
        BiometricPrompt.ERROR_HW_UNAVAILABLE -> Fallo.NoDisponible("El sensor no responde ahora mismo. Prueba en un momento o usa la contraseña.")
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> Fallo.NoDisponible("Este móvil no tiene sensor de huella que Android reconozca.")
        BiometricPrompt.ERROR_NO_BIOMETRICS -> Fallo.NoDisponible("No hay ninguna huella registrada en Android.")
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> Fallo.NoDisponible("El móvil no tiene PIN, patrón ni contraseña de bloqueo.")
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> Fallo.NoDisponible("Android pide una actualización de seguridad antes de dejar usar la huella.")
        BiometricPrompt.ERROR_TIMEOUT -> Fallo.Otro(codigo, "Se agotó el tiempo sin leer la huella.")
        BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
        BiometricPrompt.ERROR_NO_SPACE,
        BiometricPrompt.ERROR_VENDOR -> Fallo.Otro(codigo, mensaje.toString().ifBlank { "El sensor no pudo leer la huella." })
        else -> Fallo.Otro(codigo, mensaje.toString())
    }

    /**
     * Abre el prompt. Con [nivel] FUERTE y un [cipher], la huella autentica la operación
     * del Keystore (CryptoObject) y [alExito] recibe el cipher ya autenticado. Con nivel
     * COMPATIBLE no hay cipher: Android comprueba huella o PIN y [alExito] recibe null.
     * [alIntentoFallido] salta con cada dedo no reconocido, para la háptica.
     */
    fun autenticar(
        actividad: FragmentActivity,
        nivel: Nivel,
        cipher: Cipher?,
        titulo: String,
        subtitulo: String,
        alExito: (Cipher?) -> Unit,
        alFallo: (Fallo) -> Unit,
        alIntentoFallido: () -> Unit = {}
    ) {
        if (nivel == Nivel.NINGUNO) {
            alFallo(Fallo.NoDisponible("Este móvil no ofrece ninguna forma de huella o PIN utilizable."))
            return
        }
        val conCripto = nivel == Nivel.FUERTE && cipher != null
        val ejecutor = ContextCompat.getMainExecutor(actividad)
        val prompt = BiometricPrompt(
            actividad,
            ejecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                    if (conCripto) {
                        val autenticado = resultado.cryptoObject?.cipher
                        if (autenticado == null) {
                            Diagnostico.apuntar("huella", "El prompt dijo ok pero no devolvió el CryptoObject")
                            alFallo(Fallo.Otro(-1, "Android no devolvió la clave autenticada."))
                        } else {
                            alExito(autenticado)
                        }
                    } else {
                        alExito(null)
                    }
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    val fallo = clasificarError(codigo, mensaje)
                    if (fallo != Fallo.Cancelado) {
                        Diagnostico.apuntar("huella", "Prompt ($nivel) con error $codigo: $mensaje")
                    }
                    alFallo(fallo)
                }

                override fun onAuthenticationFailed() {
                    alIntentoFallido()
                }
            }
        )
        val constructor = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setConfirmationRequired(false)
        if (nivel == Nivel.FUERTE) {
            constructor
                .setAllowedAuthenticators(AUTENTICADORES_FUERTES)
                .setNegativeButtonText("Usar contraseña")
        } else {
            // Con DEVICE_CREDENTIAL Android no permite botón negativo: el propio diálogo
            // ofrece "Usar PIN" y cancelar devuelve a la contraseña maestra.
            constructor.setAllowedAuthenticators(AUTENTICADORES_COMPATIBLES)
        }
        try {
            if (conCripto) {
                prompt.authenticate(constructor.build(), BiometricPrompt.CryptoObject(cipher!!))
            } else {
                prompt.authenticate(constructor.build())
            }
        } catch (e: Exception) {
            Diagnostico.apuntar("huella", "El prompt ($nivel) no pudo abrirse", e)
            alFallo(Fallo.Otro(-1, "No se pudo abrir el diálogo de huella de Android."))
        }
    }

    /**
     * Confirmación de presencia, sin clave de por medio. Para cuando la bóveda ya está
     * abierta y lo único que hace falta es que confirmes que eres tú.
     */
    fun confirmar(
        actividad: FragmentActivity,
        titulo: String,
        subtitulo: String,
        alExito: () -> Unit,
        alFallo: (Fallo) -> Unit
    ) {
        autenticar(
            actividad = actividad,
            nivel = nivel(actividad),
            cipher = null,
            titulo = titulo,
            subtitulo = subtitulo,
            alExito = { alExito() },
            alFallo = alFallo
        )
    }
}
