package com.pepotech.pepoboveda.passkey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.Monograma
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Haptica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hoja compartida por los flujos de passkey: si la bóveda está cerrada pide la
 * contraseña maestra; cuando está abierta pide confirmación explícita.
 */
@Composable
fun HojaPasskey(
    actividad: FragmentActivity,
    repositorio: VaultRepository,
    titulo: String,
    sitio: String,
    detalle: String,
    textoAccion: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit
) {
    val haptica = remember { Haptica(actividad) }
    val ambito = rememberCoroutineScope()
    var abierta by remember { mutableStateOf(repositorio.estaDesbloqueada) }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    val biometriaLista = remember {
        repositorio.ajustes.actual.biometriaActiva &&
            repositorio.biometria.estaConfigurada &&
            Biometria.disponible(actividad)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        TarjetaPepo {
            Monograma(titulo = sitio.ifBlank { "Passkey" }, semilla = sitio, tamano = 52)
            Spacer(Modifier.height(14.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = Ambar)
            Spacer(Modifier.height(6.dp))
            Text(detalle, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Spacer(Modifier.height(18.dp))

            if (!abierta) {
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
                Spacer(Modifier.height(16.dp))
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
                            contrasena = ""
                            abierta = true
                        } else {
                            haptica.error()
                            error = "Esa no es. Vuelve a intentarlo."
                        }
                    }
                }
                if (biometriaLista) {
                    Spacer(Modifier.height(12.dp))
                    BotonBorde(texto = "Usar biometría") {
                        val cipher = try {
                            repositorio.biometria.cipherParaDesenvolver()
                        } catch (e: Exception) {
                            error = "La biometría ya no vale. Usa la contraseña."
                            null
                        }
                        if (cipher != null) {
                            Biometria.autenticar(
                                actividad = actividad,
                                cipher = cipher,
                                titulo = "Pepo Bóveda",
                                subtitulo = "Desbloquea para usar tu passkey",
                                alExito = { cifrador ->
                                    try {
                                        val clave = repositorio.biometria.leerEnvuelta(cifrador)
                                        repositorio.desbloquearConClaveMaestra(clave)
                                        haptica.exito()
                                        abierta = true
                                    } catch (e: Exception) {
                                        error = "No se pudo abrir la bóveda con biometría"
                                    }
                                },
                                alFallar = { error = it }
                            )
                        }
                    }
                }
            } else {
                error?.let {
                    Text(it, color = Peligro, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                BotonAmbar(texto = textoAccion, activo = !trabajando) {
                    trabajando = true
                    haptica.exito()
                    alConfirmar()
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Cancelar",
                style = MaterialTheme.typography.labelLarge,
                color = TextoSecundario,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { alCancelar() }
                    .padding(6.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "La clave privada se queda cifrada en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoPrincipal.copy(alpha = 0.5f)
            )
        }
    }
}
