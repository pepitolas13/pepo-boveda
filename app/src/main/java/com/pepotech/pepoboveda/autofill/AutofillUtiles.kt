package com.pepotech.pepoboveda.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.service.autofill.Dataset
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.pepotech.pepoboveda.R
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.util.Dominios

data class CamposDetectados(
    val usuario: AutofillId? = null,
    val contrasena: AutofillId? = null,
    val dominioWeb: String? = null
) {
    val hayAlgo: Boolean get() = usuario != null || contrasena != null
}

object AutofillUtiles {

    private val PISTAS_USUARIO = listOf("username", "email", "user", "correo", "usuario", "login", "identifier", "phone")
    private val PISTAS_CONTRASENA = listOf("password", "contrasena", "contraseña", "passwd", "pwd", "clave")

    fun detectar(estructura: AssistStructure): CamposDetectados {
        var usuario: AutofillId? = null
        var contrasena: AutofillId? = null
        var dominio: String? = null

        fun recorrer(nodo: AssistStructure.ViewNode) {
            nodo.webDomain?.takeIf { it.isNotBlank() }?.let { if (dominio == null) dominio = it }
            val id = nodo.autofillId
            if (id != null && nodo.autofillType == View.AUTOFILL_TYPE_TEXT) {
                val pistasSistema = nodo.autofillHints?.map { it.lowercase() } ?: emptyList()
                val textoPistas = buildList {
                    addAll(pistasSistema)
                    nodo.hint?.lowercase()?.let { add(it) }
                    nodo.idEntry?.lowercase()?.let { add(it) }
                    nodo.text?.toString()?.lowercase()?.let { add(it) }
                    nodo.htmlInfo?.attributes?.forEach { par ->
                        par.second?.lowercase()?.let { add(it) }
                    }
                }
                val esContrasenaPorTipo = (nodo.inputType and InputType.TYPE_MASK_VARIATION) ==
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                val esContrasena = esContrasenaPorTipo || textoPistas.any { pista ->
                    PISTAS_CONTRASENA.any { pista.contains(it) }
                }
                val esUsuario = textoPistas.any { pista -> PISTAS_USUARIO.any { pista.contains(it) } }
                if (esContrasena && contrasena == null) {
                    contrasena = id
                } else if (esUsuario && usuario == null) {
                    usuario = id
                }
            }
            for (i in 0 until nodo.childCount) recorrer(nodo.getChildAt(i))
        }

        for (i in 0 until estructura.windowNodeCount) {
            recorrer(estructura.getWindowNodeAt(i).rootViewNode)
        }
        return CamposDetectados(usuario, contrasena, dominio)
    }

    /** Devuelve el par (usuario, contraseña) escrito por la persona, para el flujo de guardado. */
    fun leerValores(estructura: AssistStructure): Pair<String?, String?> {
        var usuario: String? = null
        var contrasena: String? = null

        fun texto(nodo: AssistStructure.ViewNode): String? {
            val valor = nodo.autofillValue
            return when {
                valor != null && valor.isText -> valor.textValue?.toString()
                else -> nodo.text?.toString()
            }?.takeIf { it.isNotBlank() }
        }

        fun recorrer(nodo: AssistStructure.ViewNode) {
            if (nodo.autofillType == View.AUTOFILL_TYPE_TEXT) {
                val pistas = buildList {
                    nodo.autofillHints?.forEach { add(it.lowercase()) }
                    nodo.hint?.lowercase()?.let { add(it) }
                    nodo.idEntry?.lowercase()?.let { add(it) }
                }
                val esContrasenaPorTipo = (nodo.inputType and InputType.TYPE_MASK_VARIATION) ==
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                val esContrasena = esContrasenaPorTipo || pistas.any { p -> PISTAS_CONTRASENA.any { p.contains(it) } }
                val esUsuario = pistas.any { p -> PISTAS_USUARIO.any { p.contains(it) } }
                if (esContrasena && contrasena == null) contrasena = texto(nodo)
                else if (esUsuario && usuario == null) usuario = texto(nodo)
            }
            for (i in 0 until nodo.childCount) recorrer(nodo.getChildAt(i))
        }

        for (i in 0 until estructura.windowNodeCount) {
            recorrer(estructura.getWindowNodeAt(i).rootViewNode)
        }
        return usuario to contrasena
    }

    fun presentacion(contexto: Context, titulo: String, subtitulo: String): RemoteViews =
        RemoteViews(contexto.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.titulo, titulo)
            setTextViewText(R.id.subtitulo, subtitulo)
        }

    @Suppress("DEPRECATION")
    fun dataset(contexto: Context, entrada: Entrada, campos: CamposDetectados): Dataset? {
        if (!campos.hayAlgo) return null
        val vista = presentacion(
            contexto,
            entrada.titulo.ifBlank { entrada.usuario.ifBlank { "Entrada" } },
            entrada.usuario.ifBlank { entrada.urls.firstOrNull() ?: "Pepo Bóveda" }
        )
        val constructor = Dataset.Builder(vista)
        campos.usuario?.let { constructor.setValue(it, AutofillValue.forText(entrada.usuario)) }
        campos.contrasena?.let { constructor.setValue(it, AutofillValue.forText(entrada.contrasena)) }
        return try {
            constructor.build()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Identificador del contexto que pide el relleno: dominio web o paquete de la app. */
    fun contextoSolicitante(paquete: String, dominioWeb: String?): String =
        if (!dominioWeb.isNullOrBlank()) Dominios.raiz(dominioWeb) else paquete

    fun entradasCompatibles(entradas: List<Entrada>, paquete: String, dominioWeb: String?): List<Entrada> {
        val objetivo = contextoSolicitante(paquete, dominioWeb)
        // Antes también se comparaba contra Dominios.dominioDePaquete(paquete), que convierte
        // com.netflix.loquesea en "netflix.com". Cualquier APK instalada a mano podía llamarse
        // com.netflix.timoso y se le ofrecían las credenciales de Netflix. Fuera: solo vale lo
        // que el usuario haya guardado explícitamente en "Sitios o paquetes".
        return entradas.filter { entrada ->
            entrada.contrasena.isNotBlank() && entrada.urls.any { guardado ->
                Dominios.coincide(guardado, objetivo)
            }
        }
    }
}
