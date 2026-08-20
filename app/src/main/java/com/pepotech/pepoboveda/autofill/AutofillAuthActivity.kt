package com.pepotech.pepoboveda.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.PepoBovedaTheme
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Haptica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla mínima de desbloqueo que se abre desde el servicio de autorrelleno
 * cuando la bóveda está cerrada. Solo devuelve un FillResponse: nunca muestra
 * la lista completa de entradas.
 */
class AutofillAuthActivity : FragmentActivity() {

    private lateinit var repositorio: VaultRepository

    private var usuarioId: AutofillId? = null
    private var contrasenaId: AutofillId? = null
    private var paquete: String = ""
    private var dominio: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repositorio = VaultRepository.obtener(this)
        if (!repositorio.ajustes.actual.modoGrabacion) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        usuarioId = leerId(PepoAutofillService.EXTRA_USUARIO_ID)
        contrasenaId = leerId(PepoAutofillService.EXTRA_CONTRASENA_ID)
        paquete = intent.getStringExtra(PepoAutofillService.EXTRA_PAQUETE) ?: ""
        dominio = intent.getStringExtra(PepoAutofillService.EXTRA_DOMINIO)

        if (repositorio.estaDesbloqueada) {
            responder()
            return
        }

        setContent {
            PepoBovedaTheme {
                Contenido()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun leerId(clave: String): AutofillId? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(clave, AutofillId::class.java)
        } else {
            intent.getParcelableExtra(clave)
        }

    @Composable
    private fun Contenido() {
        val haptica = remember { Haptica(this) }
        var contrasena by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var trabajando by remember { mutableStateOf(false) }
        val ambito = androidx.compose.runtime.rememberCoroutineScope()
        val biometriaLista = remember {
            repositorio.ajustes.actual.biometriaActiva &&
                repositorio.biometria.estaConfigurada &&
                Biometria.disponible(this)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            TarjetaPepo {
                Text("Pepo Bóveda", style = MaterialTheme.typography.titleLarge, color = Ambar)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Desbloquea para rellenar en ${AutofillUtiles.contextoSolicitante(paquete, dominio)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSecundario
                )
                Spacer(Modifier.height(18.dp))
                CampoPepo(
                    valor = contrasena,
                    etiqueta = "Contraseña maestra",
                    alCambiar = { contrasena = it; error = null },
                    esContrasena = true
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Peligro, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
                BotonAmbar(
                    texto = if (trabajando) "Abriendo…" else "Desbloquear",
                    activo = contrasena.isNotEmpty() && !trabajando
                ) {
                    trabajando = true
                    ambito.launch {
                        val ok = withContext(Dispatchers.Default) {
                            try {
                                repositorio.desbloquear(contrasena.toCharArray())
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        trabajando = false
                        if (ok) {
                            haptica.exito()
                            responder()
                        } else {
                            haptica.error()
                            error = "Esa no es. Vuelve a intentarlo."
                        }
                    }
                }
                if (biometriaLista) {
                    Spacer(Modifier.height(12.dp))
                    BotonBorde(texto = "Usar biometría") {
                        desbloquearConBiometria { error = it }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Cancelar",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextoSecundario,
                        modifier = Modifier
                            .clickable { cancelar() }
                            .padding(6.dp)
                    )
                }
            }
        }
    }

    private fun desbloquearConBiometria(alFallar: (String) -> Unit) {
        val cipher = try {
            repositorio.biometria.cipherParaDesenvolver()
        } catch (e: Exception) {
            alFallar("La biometría ya no vale. Usa la contraseña.")
            return
        }
        Biometria.autenticar(
            actividad = this,
            cipher = cipher,
            titulo = "Pepo Bóveda",
            subtitulo = "Desbloquea para rellenar",
            alExito = { cifrador ->
                try {
                    val clave = repositorio.biometria.leerEnvuelta(cifrador)
                    repositorio.desbloquearConClaveMaestra(clave)
                    responder()
                } catch (e: Exception) {
                    alFallar("No se pudo abrir la bóveda con biometría")
                }
            },
            alFallar = { alFallar(it) }
        )
    }

    private fun responder() {
        val campos = CamposDetectados(usuarioId, contrasenaId, dominio)
        val respuesta = FillResponse.Builder()
        var alguno = false
        AutofillUtiles.entradasCompatibles(repositorio.entradas(), paquete, dominio).forEach { entrada ->
            AutofillUtiles.dataset(this, entrada, campos)?.let {
                respuesta.addDataset(it)
                alguno = true
            }
        }
        val datos = Intent()
        if (alguno) {
            datos.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, respuesta.build())
        }
        setResult(Activity.RESULT_OK, datos)
        finish()
    }

    private fun cancelar() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
