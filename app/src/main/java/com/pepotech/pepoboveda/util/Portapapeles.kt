package com.pepotech.pepoboveda.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

object Portapapeles {

    fun copiarSensible(contexto: Context, etiqueta: String, valor: String) {
        val gestor = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val datos = ClipData.newPlainText(etiqueta, valor)
        val extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        datos.description.extras = extras
        gestor.setPrimaryClip(datos)
    }

    fun limpiarSiCoincide(contexto: Context, valor: String) {
        val gestor = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val actual = gestor.primaryClip
        val texto = actual?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (texto == null || texto == valor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                gestor.clearPrimaryClip()
            } else {
                gestor.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
