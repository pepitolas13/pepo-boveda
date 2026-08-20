package com.pepotech.pepoboveda.util

object Dominios {

    private val SUFIJOS_COMPUESTOS = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.ar", "com.br", "com.mx", "com.co",
        "com.au", "com.tr", "co.jp", "co.kr", "co.nz", "com.es", "gob.es", "com.pe",
        "com.cl", "com.ve", "com.uy", "com.py", "com.ec", "com.bo", "com.pa", "com.do"
    )

    fun host(entrada: String): String {
        val texto = entrada.trim().lowercase()
        if (texto.isEmpty()) return ""
        val sinEsquema = texto.substringAfter("://", texto)
        val sinRuta = sinEsquema.substringBefore('/').substringBefore('?').substringBefore('#')
        val sinCredenciales = sinRuta.substringAfterLast('@')
        return sinCredenciales.substringBefore(':').removePrefix("www.")
    }

    /** Dominio registrable ("raíz") de una URL o host. */
    fun raiz(entrada: String): String {
        val h = host(entrada)
        if (h.isEmpty() || !h.contains('.')) return h
        val partes = h.split('.')
        if (partes.size <= 2) return h
        val ultimosDos = partes.takeLast(2).joinToString(".")
        return if (ultimosDos in SUFIJOS_COMPUESTOS && partes.size >= 3) {
            partes.takeLast(3).joinToString(".")
        } else {
            ultimosDos
        }
    }

    /** Coincidencia conservadora: mismo dominio raíz o mismo paquete de aplicación. */
    fun coincide(guardado: String, solicitado: String): Boolean {
        if (guardado.isBlank() || solicitado.isBlank()) return false
        val g = guardado.trim().lowercase()
        val s = solicitado.trim().lowercase()
        if (g == s) return true
        val esPaquete = { valor: String -> !valor.contains('/') && valor.count { it == '.' } >= 1 && !valor.contains(' ') }
        if (esPaquete(g) && esPaquete(s) && g == s) return true
        val raizGuardado = raiz(g)
        val raizSolicitado = raiz(s)
        return raizGuardado.isNotEmpty() && raizGuardado == raizSolicitado
    }

    /** Deriva un dominio candidato desde un nombre de paquete (com.ejemplo.app -> ejemplo.com). */
    fun dominioDePaquete(paquete: String): String {
        val partes = paquete.split('.')
        return if (partes.size >= 2) "${partes[1]}.${partes[0]}" else paquete
    }
}
