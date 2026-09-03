package com.pepotech.pepoboveda.data

import android.content.Context

/**
 * Cuenta los intentos fallidos de contraseña maestra y obliga a esperar entre tandas.
 *
 * Vive en disco a propósito. Antes eran dos campos del ViewModel, así que cerrar la
 * app desde recientes borraba el contador y devolvía los cinco intentos gratis: quien
 * prueba claves a mano en un móvil ajeno hace exactamente eso.
 *
 * Esto no es el freno de verdad — el freno de verdad es Argon2 sobre el archivo
 * cifrado, y contra quien se lleve el .bvda no sirve de nada porque puede probar
 * fuera de la app. Solo encarece probar a mano sobre el móvil desbloqueado.
 */
object FrenoIntentos {

    private const val FICHERO = "freno_intentos"
    private const val CLAVE_INTENTOS = "intentos"
    private const val CLAVE_HASTA = "bloqueado_hasta"

    /** Intentos antes de empezar a castigar. */
    private const val GRATIS = 5
    private const val CASTIGO_BASE_SEGUNDOS = 5L
    private const val MAX_CASTIGO_SEGUNDOS = 300L

    private fun prefs(contexto: Context) =
        contexto.applicationContext.getSharedPreferences(FICHERO, Context.MODE_PRIVATE)

    /** Segundos que faltan para poder volver a probar. 0 si se puede probar ya. */
    fun esperaSegundos(contexto: Context): Long {
        val restante = prefs(contexto).getLong(CLAVE_HASTA, 0L) - System.currentTimeMillis()
        if (restante <= 0) return 0
        // Si alguien atrasa el reloj del móvil, la espera saldría eterna. La topamos
        // en el castigo máximo: adelantar el reloj tampoco ayuda, porque el contador
        // de intentos sigue subiendo y la siguiente espera es más larga.
        val tope = MAX_CASTIGO_SEGUNDOS * 1000L
        return (minOf(restante, tope) / 1000) + 1
    }

    fun apuntarFallo(contexto: Context) {
        val prefs = prefs(contexto)
        val intentos = prefs.getInt(CLAVE_INTENTOS, 0) + 1
        val editor = prefs.edit().putInt(CLAVE_INTENTOS, intentos)
        if (intentos >= GRATIS) {
            val castigo = minOf(
                MAX_CASTIGO_SEGUNDOS,
                CASTIGO_BASE_SEGUNDOS * (1L shl minOf(6, intentos - GRATIS))
            )
            editor.putLong(CLAVE_HASTA, System.currentTimeMillis() + castigo * 1000L)
        }
        // commit() y no apply(): si matan la app justo después de fallar, el intento
        // tiene que haber llegado al disco. Es la única razón de que esto exista.
        editor.commit()
    }

    fun limpiar(contexto: Context) {
        prefs(contexto).edit().remove(CLAVE_INTENTOS).remove(CLAVE_HASTA).commit()
    }
}
