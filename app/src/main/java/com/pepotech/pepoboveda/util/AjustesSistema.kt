package com.pepotech.pepoboveda.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Abrir "Contraseñas y llaves de acceso" no es una sola pantalla igual en todos los móviles:
 * cambia según la versión de Android y según lo que haya tocado el fabricante. Así que probamos
 * en cascada, de lo más exacto a lo más genérico, y nos quedamos con el primero que exista.
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

        for (intent in candidatos) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intentar(contexto, intent)) return true
        }
        return false
    }

    private fun intentar(contexto: Context, intent: Intent): Boolean = try {
        contexto.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }
}
