package com.pepotech.pepoboveda.util

import android.content.Context
import com.pepotech.pepoboveda.R
import com.nulabinc.zxcvbn.Zxcvbn
import com.pepotech.pepoboveda.crypto.PasswordGenerator
import kotlin.math.ln
import kotlin.math.max

data class Fuerza(
    val puntuacion: Int,
    val etiqueta: String,
    val tiempo: String,
    val fraccion: Float,
    val bits: Double
)

object MedidorFuerza {

    private val zxcvbn by lazy { Zxcvbn() }

    fun medir(context: Context, contrasena: String): Fuerza {
        if (contrasena.isEmpty()) {
            return Fuerza(0, context.getString(R.string.fuerza_vacia), context.getString(R.string.fuerza_tiempo_instante), 0f, 0.0)
        }
        val recortada = if (contrasena.length > 72) contrasena.substring(0, 72) else contrasena
        val medida = zxcvbn.measure(recortada)
        val intentos = max(medida.guesses, 1.0)
        val bits = ln(intentos) / ln(2.0)
        val etiqueta = when (medida.score) {
            0 -> context.getString(R.string.fuerza_muy_debil)
            1 -> context.getString(R.string.fuerza_debil)
            2 -> context.getString(R.string.fuerza_aceptable)
            3 -> context.getString(R.string.fuerza_fuerte)
            else -> context.getString(R.string.fuerza_excelente)
        }
        return Fuerza(
            puntuacion = medida.score,
            etiqueta = etiqueta,
            tiempo = PasswordGenerator.tiempoDeCrackeo(bits),
            fraccion = ((medida.score + 1) / 5f).coerceIn(0.08f, 1f),
            bits = bits
        )
    }
}
