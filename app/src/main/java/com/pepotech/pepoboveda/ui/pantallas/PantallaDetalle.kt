package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.crypto.Totp
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.AnilloTotp
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.Monograma
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.componentes.contrasenaColoreada
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.EstiloMono
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica
import kotlinx.coroutines.delay

@Composable
fun PantallaDetalle(vm: VaultViewModel, id: String) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    val entrada = vm.entrada(id)
    var revelada by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var ultimaCopia by remember { mutableStateOf<String?>(null) }

    if (entrada == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Esta entrada ya no está en la bóveda", color = TextoSecundario)
            Spacer(Modifier.height(16.dp))
            BotonBorde("Volver") { vm.volverALista() }
        }
        return
    }

    LaunchedEffect(ultimaCopia) {
        if (ultimaCopia != null) {
            delay(1_400)
            ultimaCopia = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monograma(
                titulo = entrada.titulo.ifBlank { "?" },
                semilla = entrada.urls.firstOrNull() ?: entrada.passkey?.rpId ?: entrada.titulo,
                tamano = 58
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entrada.titulo.ifBlank { "Sin título" }, style = MaterialTheme.typography.headlineSmall, color = TextoPrincipal)
                Text(entrada.tipo.etiqueta, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            }
            IconButton(onClick = { haptica.tic(); vm.alternarFavorito(entrada.id) }) {
                Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = if (entrada.favorito) Ambar else Borde)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (entrada.usuario.isNotBlank()) {
            TarjetaPepo {
                EtiquetaSeccion("Usuario")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entrada.usuario, style = EstiloMono, color = TextoPrincipal, modifier = Modifier.weight(1f))
                    BotonCopiar(copiado = ultimaCopia == "usuario") {
                        haptica.toque()
                        vm.copiar("Usuario", entrada.usuario, sensible = false)
                        ultimaCopia = "usuario"
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (entrada.contrasena.isNotBlank()) {
            TarjetaPepo {
                EtiquetaSeccion("Contraseña")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contrasenaColoreada(entrada.contrasena),
                        style = EstiloMono,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (revelada) Modifier else Modifier.blur(9.dp))
                            .clickable {
                                haptica.toque()
                                revelada = !revelada
                            }
                    )
                    BotonCopiar(copiado = ultimaCopia == "contrasena") {
                        haptica.exito()
                        vm.copiar("Contraseña", entrada.contrasena, sensible = true)
                        ultimaCopia = "contrasena"
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (revelada) "Pulsa el texto para volver a ocultarla" else "Pulsa el texto para revelarla",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSecundario
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        entrada.secretoTotp?.takeIf { it.isNotBlank() }?.let { secreto ->
            var ahora by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
            LaunchedEffect(secreto) {
                while (true) {
                    ahora = System.currentTimeMillis() / 1000
                    delay(500)
                }
            }
            val codigo = remember(ahora / Totp.PERIODO_SEGUNDOS, secreto) {
                try {
                    Totp.codigoDesdeBase32(secreto, ahora)
                } catch (e: Exception) {
                    "------"
                }
            }
            TarjetaPepo {
                EtiquetaSeccion("Código de verificación (TOTP)")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnilloTotp(codigo = codigo, segundosRestantes = Totp.segundosRestantes(ahora))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Se renueva cada 30 s", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        BotonBorde("Copiar código", color = Menta) {
                            haptica.toque()
                            vm.copiar("Código TOTP", codigo, sensible = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (entrada.urls.isNotEmpty()) {
            TarjetaPepo {
                EtiquetaSeccion("Sitios y apps asociados")
                Spacer(Modifier.height(8.dp))
                entrada.urls.forEach { url ->
                    Text(url, style = MaterialTheme.typography.bodyLarge, color = TextoPrincipal)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (entrada.notas.isNotBlank()) {
            TarjetaPepo {
                EtiquetaSeccion("Notas")
                Spacer(Modifier.height(8.dp))
                Text(entrada.notas, style = MaterialTheme.typography.bodyLarge, color = TextoPrincipal)
            }
            Spacer(Modifier.height(12.dp))
        }

        entrada.passkey?.let { passkey ->
            TarjetaPepo {
                EtiquetaSeccion("Passkey")
                Spacer(Modifier.height(8.dp))
                Text("Servicio: ${passkey.rpName.ifBlank { passkey.rpId }}", color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
                Text("Dominio: ${passkey.rpId}", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Text("Algoritmo: ${passkey.algoritmo} (P-256)", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Text("La clave privada vive dentro del JSON cifrado de la bóveda.", color = Menta, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonBorde("Editar", modifier = Modifier.weight(1f)) { vm.ir(Pantalla.Editar(entrada.id)) }
            BotonBorde("Borrar", modifier = Modifier.weight(1f), color = Peligro) { confirmarBorrado = true }
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Volver a la bóveda") { vm.volverALista() }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Borrar esta entrada?") },
            text = { Text("Se elimina de la bóveda cifrada y no hay copia en ningún otro sitio.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = false
                    haptica.error()
                    vm.eliminar(entrada.id)
                }) { Text("Borrar", color = Peligro) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BotonCopiar(copiado: Boolean, alPulsar: () -> Unit) {
    IconButton(onClick = alPulsar) {
        AnimatedVisibility(
            visible = copiado,
            enter = scaleIn(spring(dampingRatio = 0.5f)),
            exit = scaleOut(spring(dampingRatio = 0.6f))
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Copiado", tint = Menta)
        }
        AnimatedVisibility(
            visible = !copiado,
            enter = scaleIn(spring(dampingRatio = 0.5f)),
            exit = scaleOut(spring(dampingRatio = 0.6f))
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar", tint = Ambar)
        }
    }
}

@Composable
private fun IconoEditar() {
    Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = Ambar)
}

@Composable
private fun IconoBorrar() {
    Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = Peligro)
}
