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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pepotech.pepoboveda.crypto.Zeroizar
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.ui.FlujoBiometria
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
import com.pepotech.pepoboveda.util.Diagnostico
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
    val flujo = remember { FlujoBiometria(actividad, repositorio) }
    var abierta by remember { mutableStateOf(repositorio.estaDesbloqueada) }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }

    // Se pregunta al entrar y al volver, no se cachea para toda la hoja.
    var biometriaLista by remember { mutableStateOf(false) }
    var huellaDisponible by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        // Una sola consulta al servicio de biometría para las dos preguntas.
        val capacidad = flujo.capacidadActual()
        biometriaLista = flujo.disponible(capacidad)
        huellaDisponible = Biometria.decidirNivel(capacidad) != Biometria.Nivel.NINGUNO
        onPauseOrDispose { }
    }

    var yaConfirmado by remember { mutableStateOf(false) }

    val pedirBiometria: () -> Unit = {
        error = null
        flujo.desbloquear(
            titulo = "Pepo Bóveda",
            subtitulo = "Desbloquea para usar tu passkey",
            alClave = { clave ->
                try {
                    repositorio.desbloquearConClaveMaestra(clave)
                    haptica.exito()
                    // Esa huella ya vale como confirmación: no te la pido dos veces.
                    yaConfirmado = true
                    abierta = true
                } catch (e: Exception) {
                    Diagnostico.apuntar("huella", "La clave desenvuelta no abrió la bóveda (passkey): ${e.javaClass.simpleName}")
                    error = "No se pudo abrir la bóveda con la huella. Usa la contraseña."
                } finally {
                    // El repositorio guarda su propia copia: esta se borra aquí mismo.
                    Zeroizar.borrar(clave)
                }
            },
            alFallo = { fallo ->
                if (fallo.cambiaDisponibilidad) biometriaLista = flujo.disponible()
                error = fallo.texto
            },
            alIntentoFallido = { haptica.error() }
        )
    }

    // Si hay huella, la pedimos sola en cuanto se abre la hoja: para eso está.
    // Si el usuario la cancela, debajo sigue la contraseña maestra.
    var biometriaPedida by remember { mutableStateOf(false) }
    LaunchedEffect(abierta, biometriaLista) {
        if (!abierta && biometriaLista && !biometriaPedida) {
            biometriaPedida = true
            pedirBiometria()
        }
    }

    // Con la bóveda ya abierta, la huella es la confirmación: pones el dedo y
    // firma. No hace falta pasear por dentro de Pepo Bóveda para nada.
    var firmaLanzada by remember { mutableStateOf(false) }
    LaunchedEffect(abierta, yaConfirmado, huellaDisponible) {
        if (!abierta || firmaLanzada) return@LaunchedEffect
        when {
            yaConfirmado -> {
                firmaLanzada = true
                trabajando = true
                alConfirmar()
            }
            huellaDisponible -> {
                firmaLanzada = true
                flujo.confirmar(
                    titulo = titulo,
                    subtitulo = sitio,
                    alExito = {
                        haptica.exito()
                        trabajando = true
                        alConfirmar()
                    },
                    alFallo = { fallo ->
                        // Si cancelas, te queda el botón de siempre.
                        firmaLanzada = false
                        error = fallo.texto
                    }
                )
            }
        }
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
                    BotonBorde(texto = flujo.etiquetaBoton()) { pedirBiometria() }
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
