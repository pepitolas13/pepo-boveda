package com.pepotech.pepoboveda.passkey

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.DatosPasskey
import com.pepotech.pepoboveda.data.TipoEntrada
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.ui.theme.PepoBovedaTheme

/** Confirma y crea una passkey nueva pedida por una web o app. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyCreateActivity : FragmentActivity() {

    private lateinit var repositorio: VaultRepository
    private var peticion: ProviderCreateCredentialRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repositorio = VaultRepository.obtener(this)
        if (!repositorio.ajustes.actual.modoGrabacion) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        peticion = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val solicitud = peticion?.callingRequest as? CreatePublicKeyCredentialRequest
        if (solicitud == null) {
            fallar("Pepo Bóveda solo crea passkeys")
            return
        }
        val datos = try {
            WebAuthn.leerCreacion(solicitud.requestJson)
        } catch (e: Exception) {
            fallar("Petición de passkey ilegible")
            return
        }
        if (datos.rpId.isBlank() || datos.reto.isBlank()) {
            fallar("La petición no trae ni sitio ni reto")
            return
        }

        setContent {
            PepoBovedaTheme {
                HojaPasskey(
                    actividad = this,
                    repositorio = repositorio,
                    titulo = "Crear passkey",
                    sitio = datos.rpId,
                    detalle = "${datos.rpId} quiere crear una passkey para " +
                        datos.usuario.ifBlank { "tu cuenta" } + ".",
                    textoAccion = "Crear la passkey",
                    alConfirmar = { crear(datos) },
                    alCancelar = { cancelar() }
                )
            }
        }
    }

    private fun crear(datos: WebAuthn.PeticionCreacion) {
        try {
            val par = WebAuthn.generarPar()
            val credId = WebAuthn.nuevoCredId()
            val info = peticion?.callingAppInfo
            // Si quien pide es un navegador, el origen que hay que firmar es el de la
            // web, no el de la app. Viene en callingAppInfo.origin y solo lo rellena
            // el sistema para clientes privilegiados.
            val origen = if (datos.rpId.contains('.')) {
                Origen.deWeb(datos.rpId)
            } else if (info != null) {
                Origen.deApp(info.packageName, info.signingInfo)
            } else {
                Origen.deWeb(datos.rpId)
            }
            android.util.Log.e("PepoPasskey", "CREAR rpId=${datos.rpId} origen=$origen")
            val clientData = WebAuthn.clientDataJson("webauthn.create", datos.reto, origen)
            val authData = WebAuthn.authenticatorDataRegistro(datos.rpId, credId, par.x, par.y)
            val attestation = WebAuthn.attestationObject(authData)
            val json = WebAuthn.respuestaRegistro(credId, clientData, attestation, authData, par.x, par.y)

            val passkey = DatosPasskey(
                rpId = datos.rpId,
                rpName = datos.rpName.ifBlank { datos.rpId },
                userHandle = datos.userHandle,
                credId = WebAuthn.aB64Url(credId),
                clavePrivada = WebAuthn.aB64Url(par.privadaPkcs8),
                usuario = datos.usuario
            )
            repositorio.guardarEntrada(
                Entrada(
                    id = repositorio.nuevoId(),
                    tipo = TipoEntrada.PASSKEY,
                    titulo = datos.rpName.ifBlank { datos.rpId },
                    usuario = datos.usuario,
                    urls = listOf(datos.rpId),
                    passkey = passkey
                )
            )

            val respuesta = Intent()
            PendingIntentHandler.setCreateCredentialResponse(
                respuesta,
                CreatePublicKeyCredentialResponse(json)
            )
            setResult(Activity.RESULT_OK, respuesta)
            finish()
        } catch (e: Exception) {
            fallar("No se pudo crear la passkey: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fallar(mensaje: String) {
        android.util.Log.e("PepoPasskey", "FALLO CREAR: $mensaje")
        val respuesta = Intent()
        PendingIntentHandler.setCreateCredentialException(
            respuesta,
            CreateCredentialUnknownException(mensaje)
        )
        setResult(Activity.RESULT_OK, respuesta)
        finish()
    }

    private fun cancelar() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
