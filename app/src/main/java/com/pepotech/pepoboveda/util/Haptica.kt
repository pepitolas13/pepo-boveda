package com.pepotech.pepoboveda.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptica(contexto: Context) {

    private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val gestor = contexto.getSystemService(VibratorManager::class.java)
        gestor?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        contexto.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tic() = efecto(12, 60)

    fun toque() = efecto(20, 120)

    fun exito() = patron(longArrayOf(0, 18, 60, 30), intArrayOf(0, 120, 0, 200))

    fun error() = patron(longArrayOf(0, 40, 80, 40), intArrayOf(0, 180, 0, 180))

    private fun efecto(duracion: Long, amplitud: Int) {
        val v = vibrador ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createOneShot(duracion, amplitud.coerceIn(1, 255)))
        } catch (e: Exception) {
            // sin vibración disponible
        }
    }

    private fun patron(tiempos: LongArray, amplitudes: IntArray) {
        val v = vibrador ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(tiempos, amplitudes, -1))
        } catch (e: Exception) {
            // sin vibración disponible
        }
    }
}
