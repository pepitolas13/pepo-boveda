package com.pepotech.pepoboveda.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

object Biometria {

    private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun disponible(contexto: Context): Boolean =
        BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(
        actividad: FragmentActivity,
        cipher: Cipher,
        titulo: String,
        subtitulo: String,
        alExito: (Cipher) -> Unit,
        alFallar: (String) -> Unit
    ) {
        val ejecutor = ContextCompat.getMainExecutor(actividad)
        val prompt = BiometricPrompt(
            actividad,
            ejecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                    val cifrador = resultado.cryptoObject?.cipher
                    if (cifrador == null) alFallar("No se pudo usar la clave biométrica") else alExito(cifrador)
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    alFallar(mensaje.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(AUTENTICADORES)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
