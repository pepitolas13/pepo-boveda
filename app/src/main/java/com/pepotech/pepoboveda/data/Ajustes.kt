package com.pepotech.pepoboveda.data

import android.content.Context
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val AjustesApp.modoBiometriaActivo: BiometricKeyStore.Modo?
    get() = if (biometriaActiva) BiometricKeyStore.Modo.desde(biometriaModo) else null

data class AjustesApp(
    val autoBloqueoSegundos: Int = 60,
    val portapapelesSegundos: Int = 30,
    val modoGrabacion: Boolean = false,
    val biometriaActiva: Boolean = false,
    val biometriaModo: String = "",
    val motorCamara: String = "auto"
)

class AlmacenAjustes(contexto: Context) {

    private val prefs: android.content.SharedPreferences = contexto.getSharedPreferences("ajustes_pepo_boveda", android.content.Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<AjustesApp> = _ajustes

    val actual: AjustesApp get() = _ajustes.value

    private fun leer(): AjustesApp {
        val biometriaActiva = prefs.getBoolean("biometria", false)
        var modo = prefs.getString("biometria_modo", "") ?: ""
        if (biometriaActiva && modo.isEmpty()) modo = "fuerte"
        return AjustesApp(
            autoBloqueoSegundos = prefs.getInt("auto_bloqueo", 60),
            portapapelesSegundos = prefs.getInt("portapapeles", 30),
            modoGrabacion = prefs.getBoolean("modo_grabacion", false),
            biometriaActiva = biometriaActiva,
            biometriaModo = modo,
            motorCamara = prefs.getString("motor_camara", "auto") ?: "auto"
        )
    }

    fun actualizar(bloque: (AjustesApp) -> AjustesApp) {
        val nuevo = bloque(_ajustes.value)
        prefs.edit()
            .putInt("auto_bloqueo", nuevo.autoBloqueoSegundos)
            .putInt("portapapeles", nuevo.portapapelesSegundos)
            .putBoolean("modo_grabacion", nuevo.modoGrabacion)
            .putBoolean("biometria", nuevo.biometriaActiva)
            .putString("biometria_modo", nuevo.biometriaModo)
            .putString("motor_camara", nuevo.motorCamara)
            .apply()
        _ajustes.value = nuevo
    }

    companion object {
        val OPCIONES_AUTO_BLOQUEO = listOf(
            0 to "Al cerrar la app",
            30 to "30 segundos",
            60 to "1 minuto",
            300 to "5 minutos"
        )
        val OPCIONES_PORTAPAPELES = listOf(
            15 to "15 segundos",
            30 to "30 segundos",
            60 to "1 minuto"
        )
    }
}
