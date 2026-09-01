package com.pepotech.pepoboveda.camara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Qué motor usa el escáner. Se guarda en Ajustes por su [clave]. */
enum class MotorCamara(val clave: String, val etiqueta: String) {
    /** CameraX primero; si falla, el compatible. Lo normal. */
    AUTOMATICO("auto", "Automático"),
    /** Solo CameraX (camera2 por debajo). */
    CAMERAX("camerax", "CameraX"),
    /** Solo la API antigua de cámara, la que funciona hasta en los HAL más viejos. */
    COMPATIBLE("compatible", "Compatible");

    companion object {
        fun desde(clave: String?): MotorCamara = entries.firstOrNull { it.clave == clave } ?: AUTOMATICO
    }
}

sealed interface EstadoCamara {
    object Iniciando : EstadoCamara

    /** [conImagen] es false cuando CameraX solo pudo enganchar el análisis, sin previsualización. */
    data class Funcionando(val conImagen: Boolean) : EstadoCamara

    /** [motivo] es para el usuario; [detalle] es la excepción, para el diagnóstico. */
    data class Fallo(val motivo: String, val detalle: String) : EstadoCamara
}

/** La única respuesta a "¿tenemos la cámara?", para que el escáner y el informe no discrepen. */
object PermisoCamara {
    fun concedido(contexto: Context): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
