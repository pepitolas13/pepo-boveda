package com.pepotech.pepoboveda.crypto

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.pow

data class OpcionesGenerador(
    val longitud: Int = 20,
    val mayusculas: Boolean = true,
    val minusculas: Boolean = true,
    val digitos: Boolean = true,
    val simbolos: Boolean = true,
    val modoFrase: Boolean = false,
    val palabras: Int = 5
)

object PasswordGenerator {

    const val MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val MINUSCULAS = "abcdefghijkmnopqrstuvwxyz"
    const val DIGITOS = "23456789"
    const val SIMBOLOS = "!@#\$%&*()-_=+[]{}?/.,:;"

    private val aleatorio = SecureRandom()

    fun conjunto(opciones: OpcionesGenerador): String = buildString {
        if (opciones.mayusculas) append(MAYUSCULAS)
        if (opciones.minusculas) append(MINUSCULAS)
        if (opciones.digitos) append(DIGITOS)
        if (opciones.simbolos) append(SIMBOLOS)
    }

    fun generar(opciones: OpcionesGenerador): String =
        if (opciones.modoFrase) generarFrase(opciones.palabras.coerceIn(4, 8))
        else generarAleatoria(opciones)

    fun generarFrase(numeroPalabras: Int, separador: String = "-"): String {
        val lista = Wordlist.PALABRAS
        return (0 until numeroPalabras)
            .map { lista[aleatorio.nextInt(lista.size)] }
            .joinToString(separador)
    }

    fun generarAleatoria(opciones: OpcionesGenerador): String {
        val longitud = opciones.longitud.coerceIn(8, 64)
        val grupos = buildList {
            if (opciones.mayusculas) add(MAYUSCULAS)
            if (opciones.minusculas) add(MINUSCULAS)
            if (opciones.digitos) add(DIGITOS)
            if (opciones.simbolos) add(SIMBOLOS)
        }.ifEmpty { listOf(MINUSCULAS) }
        val todos = grupos.joinToString("")
        val salida = CharArray(longitud)
        for (i in 0 until longitud) salida[i] = todos[aleatorio.nextInt(todos.length)]
        // Garantiza al menos un carácter de cada grupo seleccionado.
        val posiciones = (0 until longitud).shuffled(aleatorio)
        grupos.forEachIndexed { indice, grupo ->
            if (indice < longitud) {
                val pos = posiciones[indice]
                salida[pos] = grupo[aleatorio.nextInt(grupo.length)]
            }
        }
        val resultado = String(salida)
        salida.fill('\u0000')
        return resultado
    }

    fun entropiaBits(opciones: OpcionesGenerador): Double =
        if (opciones.modoFrase) {
            log2(Wordlist.TAMANO.toDouble()) * opciones.palabras.coerceIn(4, 8)
        } else {
            val tamano = conjunto(opciones).length.coerceAtLeast(1)
            log2(tamano.toDouble()) * opciones.longitud.coerceIn(8, 64)
        }

    private fun log2(x: Double): Double = ln(x) / ln(2.0)

    /** Estimación en lenguaje humano suponiendo 10^11 intentos por segundo. */
    fun tiempoDeCrackeo(bits: Double): String {
        if (bits <= 0) return "al instante"
        val intentosPorSegundo = 1e11
        val segundos = 2.0.pow(bits - 1) / intentosPorSegundo
        return when {
            segundos < 1 -> "menos de un segundo"
            segundos < 60 -> "${segundos.toLong()} segundos"
            segundos < 3_600 -> "${(segundos / 60).toLong()} minutos"
            segundos < 86_400 -> "${(segundos / 3_600).toLong()} horas"
            segundos < 2_592_000 -> "${(segundos / 86_400).toLong()} días"
            segundos < 31_536_000 -> "${(segundos / 2_592_000).toLong()} meses"
            segundos < 3.1536e9 -> "${(segundos / 31_536_000).toLong()} años"
            segundos < 3.1536e10 -> "${(segundos / 3.1536e9).toLong()} siglos"
            segundos < 3.1536e16 -> "${formatoGrande(segundos / 31_536_000)} años"
            else -> "más que la edad del universo"
        }
    }

    private fun formatoGrande(valor: Double): String {
        val unidades = listOf("" to 1.0, " mil" to 1e3, " millones de" to 1e6, " mil millones de" to 1e9, " billones de" to 1e12)
        for ((sufijo, factor) in unidades.reversed()) {
            if (valor >= factor) return "${(valor / factor).toLong()}$sufijo"
        }
        return valor.toLong().toString()
    }
}
