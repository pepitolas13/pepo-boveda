package com.pepotech.pepoboveda.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object RateLimitStore {

    private const val NOMBRE = "rate_limit_pepo_boveda_v2"
    private const val CLAVE_INTENTOS = "intentos_fallidos"
    private const val CLAVE_BLOQUEO = "bloqueado_hasta"
    private const val CLAVE_SAL = "device_salt"

    private const val MAX_INTENTOS = 5
    private const val CASTIGO_BASE_SEGUNDOS = 10L
    private const val MAX_CASTIGO_SEGUNDOS = 600L

    private fun huellaDispositivo(contexto: Context): String = try {
        val paquete = contexto.packageName
        val info = contexto.packageManager.getPackageInfo(paquete, PackageManager.GET_SIGNING_CERTIFICATES)
        val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return "unknown"
        val androidId = Settings.Secure.getString(contexto.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val datos = (paquete + androidId + sig.contentToString()).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(datos)
        android.util.Base64.encodeToString(digest, android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    } catch (e: Exception) {
        "unknown"
    }

    private fun prefs(contexto: Context): android.content.SharedPreferences {
        return contexto.getSharedPreferences(NOMBRE, android.content.Context.MODE_PRIVATE)
    }

    fun registrarIntento(contexto: Context) {
        val prefs = prefs(contexto)
        val actuales = prefs.getInt(CLAVE_INTENTOS, 0) + 1
        val editor = prefs.edit()
        if (actuales >= MAX_INTENTOS) {
            val extra = prefs.getInt(CLAVE_SAL, 0)
            val castigo = minOf(MAX_CASTIGO_SEGUNDOS, CASTIGO_BASE_SEGUNDOS * (1L shl minOf(6, actuales - MAX_INTENTOS + extra)))
            editor.putLong(CLAVE_BLOQUEO, System.currentTimeMillis() + castigo * 1000L)
            editor.putInt(CLAVE_SAL, extra + 1)
        }
        editor.putInt(CLAVE_INTENTOS, actuales)
        editor.apply()
    }

    fun segundosRestantes(contexto: Context): Long {
        val prefs = prefs(contexto)
        val bloqueadoHasta = prefs.getLong(CLAVE_BLOQUEO, 0L)
        val restante = bloqueadoHasta - System.currentTimeMillis()
        return if (restante > 0) (restante / 1000) + 1 else 0L
    }

    fun limpiar(contexto: Context) {
        prefs(contexto).edit()
            .remove(CLAVE_INTENTOS)
            .remove(CLAVE_BLOQUEO)
            .remove(CLAVE_SAL)
            .apply()
    }
}
