package com.pepotech.pepoboveda.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AjustesApp(
    val autoBloqueoSegundos: Int = 60,
    val portapapelesSegundos: Int = 30,
    val modoGrabacion: Boolean = false,
    val biometriaActiva: Boolean = false
)

class AlmacenAjustes(contexto: Context) {

    private val prefs: SharedPreferences =
        contexto.getSharedPreferences("ajustes_pepo_boveda", Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<AjustesApp> = _ajustes

    val actual: AjustesApp get() = _ajustes.value

    private fun leer() = AjustesApp(
        autoBloqueoSegundos = prefs.getInt("auto_bloqueo", 60),
        portapapelesSegundos = prefs.getInt("portapapeles", 30),
        modoGrabacion = prefs.getBoolean("modo_grabacion", false),
        biometriaActiva = prefs.getBoolean("biometria", false)
    )

    fun actualizar(bloque: (AjustesApp) -> AjustesApp) {
        val nuevo = bloque(_ajustes.value)
        prefs.edit()
            .putInt("auto_bloqueo", nuevo.autoBloqueoSegundos)
            .putInt("portapapeles", nuevo.portapapelesSegundos)
            .putBoolean("modo_grabacion", nuevo.modoGrabacion)
            .putBoolean("biometria", nuevo.biometriaActiva)
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
