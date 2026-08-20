package com.pepotech.pepoboveda.crypto

object Base32 {

    private const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun esValido(texto: String): Boolean = try {
        decodificar(texto).isNotEmpty()
    } catch (e: IllegalArgumentException) {
        false
    }

    fun decodificar(texto: String): ByteArray {
        val limpio = texto.uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        require(limpio.isNotEmpty()) { "Secreto vacío" }
        var buffer = 0
        var bits = 0
        val salida = ArrayList<Byte>(limpio.length * 5 / 8 + 1)
        for (c in limpio) {
            val indice = ALFABETO.indexOf(c)
            require(indice >= 0) { "Carácter Base32 inválido: $c" }
            buffer = (buffer shl 5) or indice
            bits += 5
            if (bits >= 8) {
                bits -= 8
                salida.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        require(salida.isNotEmpty()) { "Secreto Base32 demasiado corto" }
        return salida.toByteArray()
    }

    fun codificar(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALFABETO[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALFABETO[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }
}
