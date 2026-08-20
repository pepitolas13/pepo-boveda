package com.pepotech.pepoboveda.passkey

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.ui.theme.PepoBovedaTheme

/** Confirma y firma una aserción con una passkey ya guardada. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyGetActivity : FragmentActivity() {

    private lateinit var repositorio: VaultRepository
    private var peticion: ProviderGetCredentialRequest? = null
    private var opcionPeticion: GetPublicKeyCredentialOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repositorio = VaultRepository.obtener(this)
        if (!repositorio.ajustes.actual.modoGrabacion) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        peticion = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val opcion = peticion?.credentialOptions?.firstOrNull { it is GetPublicKeyCredentialOption }
            as? GetPublicKeyCredentialOption
        if (opcion == null) {
            fallar("Pepo Bóveda solo responde a passkeys")
            return
        }
        opcionPeticion = opcion
        val datos = try {
            WebAuthn.leerAsercion(opcion.requestJson)
        } catch (e: Exception) {
            fallar("Petición de passkey ilegible")
            return
        }
        val entradaId = intent.getStringExtra(PepoCredentialProviderService.EXTRA_ENTRADA_ID)

        setContent {
            PepoBovedaTheme {
                HojaPasskey(
                    actividad = this,
                    repositorio = repositorio,
                    titulo = "Entrar con passkey",
                    sitio = datos.rpId,
                    detalle = "${datos.rpId} quiere que firmes con tu passkey. " +
                        "Pepo Bóveda firma el reto sin enviar tu clave privada.",
                    textoAccion = "Firmar y entrar",
                    alConfirmar = { firmar(datos, entradaId) },
                    alCancelar = { cancelar() }
                )
            }
        }
    }

    private fun elegir(datos: WebAuthn.PeticionAsercion, entradaId: String?): Entrada? {
        val candidatas = repositorio.passkeysDe(datos.rpId).filter { entrada ->
            val passkey = entrada.passkey ?: return@filter false
            datos.credencialesPermitidas.isEmpty() ||
                datos.credencialesPermitidas.contains(passkey.credId)
        }
        return candidatas.firstOrNull { it.id == entradaId } ?: candidatas.firstOrNull()
    }

    private fun firmar(datos: WebAuthn.PeticionAsercion, entradaId: String?) {
        try {
            val entrada = elegir(datos, entradaId)
            val passkey = entrada?.passkey
            if (passkey == null) {
                fallar("No hay ninguna passkey guardada para ${datos.rpId}")
                return
            }
            val info = peticion?.callingAppInfo
            val origen = if (info != null) Origen.deApp(info.packageName, info.signingInfo) else Origen.deWeb(datos.rpId)
            val clientData = WebAuthn.clientDataJson("webauthn.get", datos.reto, origen)
            val authData = WebAuthn.authenticatorDataAsercion(datos.rpId, 0)
            // Si quien pide es un navegador privilegiado, el hash lo trae él.
            val hashCliente = opcionPeticion?.clientDataHash ?: WebAuthn.sha256(clientData)
            val firma = WebAuthn.firmar(
                WebAuthn.deB64Url(passkey.clavePrivada),
                authData + hashCliente
            )
            val json = WebAuthn.respuestaAsercion(
                credId = WebAuthn.deB64Url(passkey.credId),
                clientData = clientData,
                authData = authData,
                firma = firma,
                userHandle = passkey.userHandle.takeIf { it.isNotBlank() }?.let { WebAuthn.deB64Url(it) }
            )
            val respuesta = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                respuesta,
                GetCredentialResponse(PublicKeyCredential(json))
            )
            setResult(Activity.RESULT_OK, respuesta)
            finish()
        } catch (e: Exception) {
            fallar("No se pudo firmar con la passkey")
        }
    }

    private fun fallar(mensaje: String) {
        val respuesta = Intent()
        PendingIntentHandler.setGetCredentialException(
            respuesta,
            GetCredentialUnknownException(mensaje)
        )
        setResult(Activity.RESULT_OK, respuesta)
        finish()
    }

    private fun cancelar() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
