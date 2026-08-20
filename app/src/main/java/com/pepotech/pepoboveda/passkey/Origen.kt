package com.pepotech.pepoboveda.passkey

import android.content.pm.SigningInfo

/**
 * Origen que se firma dentro de clientDataJSON. Para apps nativas Android usa
 * el formato oficial android:apk-key-hash:<sha256 del certificado de firma>.
 */
object Origen {

    fun deApp(paquete: String, firma: SigningInfo?): String {
        val certificados = try {
            firma?.apkContentsSigners
        } catch (e: Exception) {
            null
        }
        val primero = certificados?.firstOrNull()
            ?: return "android:apk-key-hash-unknown:$paquete"
        val hash = WebAuthn.sha256(primero.toByteArray())
        return "android:apk-key-hash:" + WebAuthn.aB64Url(hash)
    }

    fun deWeb(rpId: String): String = "https://$rpId"
}
