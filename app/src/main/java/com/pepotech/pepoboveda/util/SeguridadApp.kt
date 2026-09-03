package com.pepotech.pepoboveda.util

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Utilidades para detectar si el entorno de ejecución es seguro.
 * Se centra en detectar Root, herramientas de depuración y ROMs personalizadas.
 */
object SeguridadApp {

    /**
     * Devuelve true si detecta señales claras de que el dispositivo está comprometido
     * o en un estado que facilita el compromiso de los datos de la bóveda.
     */
    fun estaComprometido(contexto: Context): Boolean {
        return esRoot() || esDebuggeado() || tieneAppsSospechosas(contexto)
    }

    /**
     * Detección básica de Root mediante binarios y firmas del sistema.
     */
    fun esRoot(): Boolean {
        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) return true

        val rutas = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        try {
            for (ruta in rutas) {
                if (File(ruta).exists()) return true
            }
        } catch (e: Exception) {
            // Ignorar fallos de lectura
        }

        // Intento de ejecución de su (opcional, puede ser ruidoso)
        return try {
            Runtime.getRuntime().exec("which su").inputStream.bufferedReader().readLine() != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detecta si hay un debugger enganchado.
     */
    fun esDebuggeado(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }

    /**
     * Busca paquetes de aplicaciones comunes para gestionar el Root o instrumentar.
     */
    fun tieneAppsSospechosas(contexto: Context): Boolean {
        val paquetes = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zacharee1.systemuituner",
            "com.topjohnwu.magisk"
        )
        val pm = contexto.packageManager
        for (paquete in paquetes) {
            try {
                pm.getPackageInfo(paquete, 0)
                return true
            } catch (e: Exception) {
                // Paquete no encontrado, continuamos
            }
        }
        return false
    }
}
