package com.pepotech.pepoboveda.passkey

import java.io.ByteArrayOutputStream

/**
 * Codificador CBOR mínimo (RFC 8949) con lo justo para WebAuthn:
 * enteros, cadenas de texto, cadenas de bytes y mapas de tamaño conocido.
 */
object Cbor {

    private const val MAYOR_ENTERO_POSITIVO = 0
    private const val MAYOR_ENTERO_NEGATIVO = 1
    private const val MAYOR_BYTES = 2
    private const val MAYOR_TEXTO = 3
    private const val MAYOR_MAPA = 5

    private fun cabecera(salida: ByteArrayOutputStream, mayor: Int, valor: Long) {
        val tipo = mayor shl 5
        when {
            valor < 24 -> salida.write(tipo or valor.toInt())
            valor < 256 -> {
                salida.write(tipo or 24)
                salida.write(valor.toInt())
            }
            valor < 65536 -> {
                salida.write(tipo or 25)
                salida.write((valor shr 8).toInt() and 0xFF)
                salida.write(valor.toInt() and 0xFF)
            }
            else -> {
                salida.write(tipo or 26)
                for (desplazamiento in intArrayOf(24, 16, 8, 0)) {
                    salida.write((valor shr desplazamiento).toInt() and 0xFF)
                }
            }
        }
    }

    private fun escribir(salida: ByteArrayOutputStream, valor: Any?) {
        when (valor) {
            is Int -> escribir(salida, valor.toLong())
            is Long -> if (valor >= 0) {
                cabecera(salida, MAYOR_ENTERO_POSITIVO, valor)
            } else {
                cabecera(salida, MAYOR_ENTERO_NEGATIVO, -1L - valor)
            }
            is String -> {
                val bytes = valor.toByteArray(Charsets.UTF_8)
                cabecera(salida, MAYOR_TEXTO, bytes.size.toLong())
                salida.write(bytes)
            }
            is ByteArray -> {
                cabecera(salida, MAYOR_BYTES, valor.size.toLong())
                salida.write(valor)
            }
            is Map<*, *> -> {
                cabecera(salida, MAYOR_MAPA, valor.size.toLong())
                valor.forEach { (clave, contenido) ->
                    escribir(salida, clave)
                    escribir(salida, contenido)
                }
            }
            else -> throw IllegalArgumentException("Tipo no soportado en CBOR: $valor")
        }
    }

    /** Los mapas se codifican en el orden de inserción (usa LinkedHashMap). */
    fun codificar(valor: Any?): ByteArray {
        val salida = ByteArrayOutputStream()
        escribir(salida, valor)
        return salida.toByteArray()
    }

    /** Clave pública COSE_Key para ES256 (P-256). */
    fun clavePublicaCose(x: ByteArray, y: ByteArray): ByteArray {
        val mapa = LinkedHashMap<Any, Any>()
        mapa[1] = 2      // kty: EC2
        mapa[3] = -7     // alg: ES256
        mapa[-1] = 1     // crv: P-256
        mapa[-2] = x
        mapa[-3] = y
        return codificar(mapa)
    }
}
