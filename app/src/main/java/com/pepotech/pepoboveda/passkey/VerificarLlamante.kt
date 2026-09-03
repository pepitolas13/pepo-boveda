package com.pepotech.pepoboveda.passkey

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object VerificarLlamante {

    private const val HashEsperado = ""

    fun esLlamanteValido(contexto: Context, paqueteEsperado: String?): Boolean {
        val paquete = paqueteEsperado ?: return false
        if (paquete == "android" || paquete == "com.google.android.gms" || paquete == contexto.packageName) {
            return true
        }
        return tieneFirmaValida(contexto, paquete)
    }

    fun tieneFirmaValida(contexto: Context, paquete: String): Boolean {
        return try {
            val info = contexto.packageManager.getPackageInfo(
                paquete,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            )
            val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return false
            val hash = MessageDigest.getInstance("SHA-256").digest(sig)
            val b64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
            b64 == HashEsperado || esCertificadoDebug(contexto, paquete)
        } catch (e: Exception) {
            false
        }
    }

    private fun esCertificadoDebug(contexto: Context, paquete: String): Boolean {
        return try {
            val info = contexto.packageManager.getPackageInfo(
                paquete,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            )
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return false
            sigs.any { cert ->
                val hash = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                hash.copyOfRange(0, 20).all { it.toInt() and 0xFF == 0 }
            }
        } catch (e: Exception) {
            false
        }
    }
}
