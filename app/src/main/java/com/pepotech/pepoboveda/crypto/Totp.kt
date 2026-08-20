package com.pepotech.pepoboveda.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {

    const val PERIODO_SEGUNDOS = 30L

    fun codigo(
        secreto: ByteArray,
        segundosUnix: Long,
        digitos: Int = 6,
        periodo: Long = PERIODO_SEGUNDOS,
        algoritmo: String = "HmacSHA1"
    ): String {
        val contador = segundosUnix / periodo
        return hotp(secreto, contador, digitos, algoritmo)
    }

    fun hotp(secreto: ByteArray, contador: Long, digitos: Int, algoritmo: String = "HmacSHA1"): String {
        val mensaje = ByteArray(8)
        var valor = contador
        for (i in 7 downTo 0) {
            mensaje[i] = (valor and 0xFF).toByte()
            valor = valor ushr 8
        }
        val mac = Mac.getInstance(algoritmo)
        mac.init(SecretKeySpec(secreto, "RAW"))
        val hash = mac.doFinal(mensaje)
        val desplazamiento = (hash[hash.size - 1].toInt() and 0x0F)
        val binario = ((hash[desplazamiento].toInt() and 0x7F) shl 24) or
            ((hash[desplazamiento + 1].toInt() and 0xFF) shl 16) or
            ((hash[desplazamiento + 2].toInt() and 0xFF) shl 8) or
            (hash[desplazamiento + 3].toInt() and 0xFF)
        var modulo = 1
        repeat(digitos) { modulo *= 10 }
        val codigo = binario % modulo
        return codigo.toString().padStart(digitos, '0')
    }

    fun segundosRestantes(segundosUnix: Long, periodo: Long = PERIODO_SEGUNDOS): Long =
        periodo - (segundosUnix % periodo)

    fun codigoDesdeBase32(secretoBase32: String, segundosUnix: Long, digitos: Int = 6): String =
        codigo(Base32.decodificar(secretoBase32), segundosUnix, digitos)
}
