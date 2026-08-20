package com.pepotech.pepoboveda.passkey

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.pepotech.pepoboveda.data.VaultRepository

/**
 * Provee passkeys al sistema. Solo publica entradas que coinciden con el rpId
 * pedido, y todo lo delicado ocurre en las actividades con confirmación.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PepoCredentialProviderService : CredentialProviderService() {

    companion object {
        const val EXTRA_ENTRADA_ID = "pepo.passkey.entrada"
        const val EXTRA_OPCION_ID = "pepo.passkey.opcion"
        private const val PETICION_CREAR = 2001
        private const val PETICION_OBTENER = 2002
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException("Pepo Bóveda solo guarda passkeys"))
            return
        }
        val peticion = try {
            WebAuthn.leerCreacion(request.requestJson)
        } catch (e: Exception) {
            callback.onError(CreateCredentialUnknownException("Petición de passkey ilegible"))
            return
        }
        if (!peticion.algoritmosSoportados) {
            callback.onError(CreateCredentialUnknownException("Pepo Bóveda solo firma con ES256"))
            return
        }
        val intent = Intent(this, PasskeyCreateActivity::class.java)
        val pendiente = PendingIntent.getActivity(
            this,
            PETICION_CREAR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val etiqueta = peticion.usuario.ifBlank { peticion.rpId.ifBlank { "Pepo Bóveda" } }
        val respuesta = BeginCreateCredentialResponse.Builder()
            .addCreateEntry(CreateEntry(etiqueta, pendiente))
            .build()
        callback.onResult(respuesta)
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val repositorio = VaultRepository.obtener(this)
        val constructor = BeginGetCredentialResponse.Builder()
        var alguna = false

        request.beginGetCredentialOptions.forEach { opcion ->
            if (opcion !is BeginGetPublicKeyCredentialOption) return@forEach
            val peticion = try {
                WebAuthn.leerAsercion(opcion.requestJson)
            } catch (e: Exception) {
                return@forEach
            }
            if (!repositorio.estaDesbloqueada) {
                // Con la bóveda cerrada no sabemos qué hay dentro: ofrecemos una
                // entrada genérica que abrirá el desbloqueo.
                val pendiente = pendienteObtener(null, opcion.id)
                constructor.addCredentialEntry(
                    PublicKeyCredentialEntry(
                        context = this,
                        username = "Desbloquear Pepo Bóveda",
                        pendingIntent = pendiente,
                        beginGetPublicKeyCredentialOption = opcion
                    )
                )
                alguna = true
                return@forEach
            }
            val candidatas = repositorio.passkeysDe(peticion.rpId).filter { entrada ->
                val datos = entrada.passkey ?: return@filter false
                peticion.credencialesPermitidas.isEmpty() ||
                    peticion.credencialesPermitidas.contains(datos.credId)
            }
            candidatas.forEach { entrada ->
                val datos = entrada.passkey ?: return@forEach
                constructor.addCredentialEntry(
                    PublicKeyCredentialEntry(
                        context = this,
                        username = datos.usuario.ifBlank { entrada.titulo.ifBlank { datos.rpId } },
                        pendingIntent = pendienteObtener(entrada.id, opcion.id),
                        beginGetPublicKeyCredentialOption = opcion
                    )
                )
                alguna = true
            }
        }

        if (!alguna) {
            callback.onError(GetCredentialUnknownException("Pepo Bóveda no tiene passkeys para este sitio"))
            return
        }
        callback.onResult(constructor.build())
    }

    private fun pendienteObtener(entradaId: String?, opcionId: String): PendingIntent {
        val intent = Intent(this, PasskeyGetActivity::class.java).apply {
            putExtra(EXTRA_ENTRADA_ID, entradaId)
            putExtra(EXTRA_OPCION_ID, opcionId)
        }
        return PendingIntent.getActivity(
            this,
            PETICION_OBTENER + (entradaId?.hashCode() ?: 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }
}
