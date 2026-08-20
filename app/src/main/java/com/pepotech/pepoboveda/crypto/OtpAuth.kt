package com.pepotech.pepoboveda.crypto

/**
 * Lector de los QR de doble factor. El formato es el mismo que usa Google Authenticator:
 * otpauth://totp/Emisor:cuenta?secret=BASE32&issuer=Emisor&digits=6&period=30
 *
 * Todo se resuelve aquí dentro: el QR no se envía a ningún sitio.
 */
object OtpAuth {

    data class Semilla(
        val emisor: String,
        val cuenta: String,
        val secreto: String,
        val digitos: Int = 6,
        val periodo: Int = 30
    ) {
        val titulo: String get() = emisor.ifBlank { cuenta.ifBlank { "2FA" } }
    }

    /** Devuelve null si el texto no es un otpauth de TOTP utilizable. */
    fun leer(texto: String): Semilla? {
        val limpio = texto.trim()
        if (!limpio.startsWith("otpauth://totp/", ignoreCase = true)) {
            // También aceptamos un secreto Base32 pegado a pelo.
            val soloSecreto = limpio.replace(" ", "").uppercase()
            return if (soloSecreto.isNotEmpty() && Base32.esValido(soloSecreto)) {
                Semilla(emisor = "", cuenta = "", secreto = soloSecreto)
            } else {
                null
            }
        }

        val sinEsquema = limpio.substring("otpauth://totp/".length)
        val etiqueta = decodificar(sinEsquema.substringBefore('?'))
        val consulta = sinEsquema.substringAfter('?', "")

        val parametros = HashMap<String, String>()
        consulta.split('&').forEach { par ->
            if (par.isBlank()) return@forEach
            val clave = par.substringBefore('=').lowercase()
            val valor = decodificar(par.substringAfter('=', ""))
            if (clave.isNotEmpty()) parametros[clave] = valor
        }

        val secreto = (parametros["secret"] ?: return null).replace(" ", "").uppercase()
        if (secreto.isEmpty() || !Base32.esValido(secreto)) return null

        val emisorEtiqueta = if (etiqueta.contains(':')) etiqueta.substringBefore(':').trim() else ""
        val cuenta = if (etiqueta.contains(':')) etiqueta.substringAfter(':').trim() else etiqueta.trim()

        return Semilla(
            emisor = (parametros["issuer"] ?: emisorEtiqueta).trim(),
            cuenta = cuenta,
            secreto = secreto,
            digitos = parametros["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6,
            periodo = parametros["period"]?.toIntOrNull()?.coerceIn(10, 300) ?: 30
        )
    }

    private fun decodificar(bruto: String): String {
        if (bruto.isEmpty()) return ""
        val salida = StringBuilder()
        var i = 0
        val bytes = ArrayList<Byte>()
        fun volcar() {
            if (bytes.isEmpty()) return
            salida.append(String(bytes.toByteArray(), Charsets.UTF_8))
            bytes.clear()
        }
        while (i < bruto.length) {
            val c = bruto[i]
            when {
                c == '%' && i + 2 < bruto.length -> {
                    val hex = bruto.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) {
                        volcar()
                        salida.append(c)
                        i++
                    } else {
                        bytes.add(hex.toByte())
                        i += 3
                    }
                }
                c == '+' -> {
                    volcar()
                    salida.append(' ')
                    i++
                }
                else -> {
                    volcar()
                    salida.append(c)
                    i++
                }
            }
        }
        volcar()
        return salida.toString()
    }
}
