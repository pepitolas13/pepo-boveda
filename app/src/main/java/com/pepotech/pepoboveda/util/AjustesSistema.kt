package com.pepotech.pepoboveda.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager

/**
 * Abrir una pantalla concreta de Ajustes no es igual en todos los móviles: cambia según
 * la versión de Android y según lo que haya tocado el fabricante. Así que probamos en
 * cascada, de lo más exacto a lo más genérico, y nos quedamos con el primero que exista.
 */
object AjustesSistema {

    /** Pantalla donde se elige el gestor de contraseñas y passkeys. Android 14+. */
    private const val ACCION_PROVEEDOR_CREDENCIALES = "android.settings.CREDENTIAL_PROVIDER"

    /** Devuelve false si este móvil no abrió ninguna de las pantallas. */
    fun abrirProveedorCredenciales(contexto: Context): Boolean {
        val paquete = Uri.parse("package:${contexto.packageName}")
        val candidatos = ArrayList<Intent>()

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: la pantalla exacta, ya filtrada por nuestra app.
            candidatos += Intent(ACCION_PROVEEDOR_CREDENCIALES, paquete)
            candidatos += Intent(ACCION_PROVEEDOR_CREDENCIALES)
        }
        // Android 13 y anteriores, y algunos fabricantes: el selector de autorrelleno,
        // que en la mayoría de capas es la misma pantalla de "Contraseñas y autocompletar".
        candidatos += Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, paquete)
        candidatos += Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
        // Último recurso: los ajustes del sistema, para que al menos no se quede en nada.
        candidatos += Intent(Settings.ACTION_SETTINGS)

        return abrirPrimero(contexto, candidatos)
    }

    /**
     * La ficha de la app en Ajustes del sistema: es el único sitio donde se puede volver a
     * dar un permiso que Android ya no deja pedir desde dentro de la app.
     */
    fun abrirFichaApp(contexto: Context): Boolean {
        val paquete = Uri.parse("package:${contexto.packageName}")
        return abrirPrimero(
            contexto,
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paquete),
                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
    }

    /** Registro de huella del sistema, para quien no tiene ninguna dada de alta. */
    fun abrirRegistroHuella(contexto: Context): Boolean {
        val candidatos = ArrayList<Intent>()
        if (Build.VERSION.SDK_INT >= 30) {
            candidatos += Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        }
        candidatos += Intent(Settings.ACTION_FINGERPRINT_ENROLL)
        candidatos += Intent(Settings.ACTION_SECURITY_SETTINGS)
        candidatos += Intent(Settings.ACTION_SETTINGS)
        return abrirPrimero(contexto, candidatos)
    }

    private fun abrirPrimero(contexto: Context, candidatos: List<Intent>): Boolean {
        for (intent in candidatos) {
            if (abrir(contexto, intent)) return true
        }
        return false
    }

    /** Lanza un intent y devuelve false si este móvil no tiene quien lo atienda. */
    fun abrir(contexto: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }
}
