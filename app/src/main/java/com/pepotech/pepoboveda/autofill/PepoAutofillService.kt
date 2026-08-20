package com.pepotech.pepoboveda.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.TipoEntrada
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.util.Dominios

class PepoAutofillService : AutofillService() {

    companion object {
        const val EXTRA_USUARIO_ID = "pepo.usuario.id"
        const val EXTRA_CONTRASENA_ID = "pepo.contrasena.id"
        const val EXTRA_PAQUETE = "pepo.paquete"
        const val EXTRA_DOMINIO = "pepo.dominio"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val contexto = request.fillContexts.lastOrNull()
        if (contexto == null) {
            callback.onSuccess(null)
            return
        }
        val estructura = contexto.structure
        val campos = AutofillUtiles.detectar(estructura)
        if (!campos.hayAlgo) {
            callback.onSuccess(null)
            return
        }
        val paquete = estructura.activityComponent?.packageName ?: ""
        val repositorio = VaultRepository.obtener(this)
        val respuesta = FillResponse.Builder()
        val ids: Array<AutofillId> = listOfNotNull(campos.usuario, campos.contrasena).toTypedArray()

        if (!repositorio.estaDesbloqueada) {
            val intent = Intent(this, AutofillAuthActivity::class.java).apply {
                putExtra(EXTRA_USUARIO_ID, campos.usuario)
                putExtra(EXTRA_CONTRASENA_ID, campos.contrasena)
                putExtra(EXTRA_PAQUETE, paquete)
                putExtra(EXTRA_DOMINIO, campos.dominioWeb)
            }
            val pendiente = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            respuesta.setAuthentication(
                ids,
                pendiente.intentSender,
                AutofillUtiles.presentacion(this, "Pepo Bóveda está cerrada", "Toca para desbloquearla")
            )
        } else {
            val compatibles = AutofillUtiles.entradasCompatibles(repositorio.entradas(), paquete, campos.dominioWeb)
            compatibles.forEach { entrada ->
                AutofillUtiles.dataset(this, entrada, campos)?.let { respuesta.addDataset(it) }
            }
        }

        if (ids.isNotEmpty()) {
            val tipos = SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            respuesta.setSaveInfo(
                SaveInfo.Builder(tipos, ids)
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build()
            )
        }

        callback.onSuccess(respuesta.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val repositorio = VaultRepository.obtener(this)
        if (!repositorio.estaDesbloqueada) {
            callback.onFailure("Abre Pepo Bóveda para guardar esta contraseña")
            return
        }
        val contexto = request.fillContexts.lastOrNull()
        if (contexto == null) {
            callback.onFailure("No se pudo leer el formulario")
            return
        }
        val estructura = contexto.structure
        val campos = AutofillUtiles.detectar(estructura)
        val (usuario, contrasena) = AutofillUtiles.leerValores(estructura)
        if (contrasena.isNullOrBlank()) {
            callback.onFailure("No se encontró ninguna contraseña que guardar")
            return
        }
        val paquete = estructura.activityComponent?.packageName ?: ""
        val objetivo = AutofillUtiles.contextoSolicitante(paquete, campos.dominioWeb)
        val titulo = if (campos.dominioWeb.isNullOrBlank()) {
            Dominios.dominioDePaquete(paquete)
        } else {
            Dominios.raiz(campos.dominioWeb!!)
        }
        val existente = repositorio.entradas().firstOrNull { entrada ->
            entrada.usuario == (usuario ?: "") && entrada.urls.any { Dominios.coincide(it, objetivo) }
        }
        val entrada = existente?.copy(contrasena = contrasena) ?: Entrada(
            id = repositorio.nuevoId(),
            tipo = TipoEntrada.LOGIN,
            titulo = titulo.ifBlank { "Nueva entrada" },
            usuario = usuario ?: "",
            contrasena = contrasena,
            urls = listOf(objetivo)
        )
        try {
            repositorio.guardarEntrada(entrada)
            callback.onSuccess()
        } catch (e: Exception) {
            callback.onFailure("No se pudo guardar en la bóveda")
        }
    }
}
