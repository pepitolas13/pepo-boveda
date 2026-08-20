package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.EstadoBoveda
import com.pepotech.pepoboveda.data.TipoEntrada
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.IlustracionVacio
import com.pepotech.pepoboveda.ui.componentes.Monograma
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.DegradadoAmbar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(vm: VaultViewModel, estado: EstadoBoveda) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    val busqueda by vm.busqueda.collectAsStateWithLifecycle()
    val filtro by vm.filtroTipo.collectAsStateWithLifecycle()
    val soloFavoritos by vm.soloFavoritos.collectAsStateWithLifecycle()
    val entradas = (estado as? EstadoBoveda.Desbloqueada)?.entradas ?: emptyList()
    val visibles = remember(entradas, busqueda, filtro, soloFavoritos) { vm.entradasVisibles(entradas) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pepo Bóveda", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
                    Text(
                        "${entradas.size} ${if (entradas.size == 1) "entrada" else "entradas"} cifradas aquí dentro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario
                    )
                }
                IconButton(onClick = { vm.ir(Pantalla.Generador) }) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "Generador", tint = Ambar)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    IconButton(onClick = { vm.ir(Pantalla.Passkeys) }) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "Passkeys", tint = TextoPrincipal)
                    }
                }
                IconButton(onClick = { vm.ir(Pantalla.Autenticador) }) {
                    Icon(Icons.Filled.Timer, contentDescription = "Autenticador 2FA", tint = TextoPrincipal)
                }
                IconButton(onClick = { vm.ir(Pantalla.Ajustes) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = TextoPrincipal)
                }
                IconButton(onClick = { haptica.toque(); vm.bloquear() }) {
                    Icon(Icons.Filled.Lock, contentDescription = "Bloquear", tint = TextoPrincipal)
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                CampoPepo(valor = busqueda, etiqueta = "Buscar", alCambiar = { vm.buscar(it) })
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipFiltro("Todo", filtro == null && !soloFavoritos) {
                    vm.filtrarPorTipo(null)
                    if (soloFavoritos) vm.alternarSoloFavoritos()
                }
                ChipFiltro("Claves", filtro == TipoEntrada.LOGIN) { vm.filtrarPorTipo(if (filtro == TipoEntrada.LOGIN) null else TipoEntrada.LOGIN) }
                ChipFiltro("Passkeys", filtro == TipoEntrada.PASSKEY) { vm.filtrarPorTipo(if (filtro == TipoEntrada.PASSKEY) null else TipoEntrada.PASSKEY) }
                ChipFiltro("Notas", filtro == TipoEntrada.NOTA) { vm.filtrarPorTipo(if (filtro == TipoEntrada.NOTA) null else TipoEntrada.NOTA) }
                ChipFiltro("�~.", soloFavoritos) { vm.alternarSoloFavoritos() }
            }

            Spacer(Modifier.height(12.dp))

            if (visibles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (entradas.isEmpty()) {
                        IlustracionVacio()
                    } else {
                        Text(
                            "Nada coincide con esa búsqueda",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextoSecundario,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibles, key = { it.id }) { entrada ->
                        FilaEntrada(
                            entrada = entrada,
                            alAbrir = { vm.ir(Pantalla.Detalle(entrada.id)) },
                            alCopiarUsuario = {
                                haptica.toque()
                                vm.copiar("Usuario", entrada.usuario, sensible = false)
                            },
                            alCopiarContrasena = {
                                haptica.exito()
                                vm.copiar("Contraseña", entrada.contrasena, sensible = true)
                            },
                            alFavorito = { haptica.tic(); vm.alternarFavorito(entrada.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { haptica.toque(); vm.ir(Pantalla.Editar(null)) },
            containerColor = Ambar,
            contentColor = Obsidiana,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nueva entrada")
        }
    }
}

@Composable
private fun ChipFiltro(texto: String, activo: Boolean, alPulsar: () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(forma)
            .background(if (activo) DegradadoAmbar else Brush.horizontalGradient(listOf(Superficie, Superficie)))
            .clickable { alPulsar() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            texto,
            color = if (activo) Obsidiana else TextoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilaEntrada(
    entrada: Entrada,
    alAbrir: () -> Unit,
    alCopiarUsuario: () -> Unit,
    alCopiarContrasena: () -> Unit,
    alFavorito: () -> Unit
) {
    val estadoSwipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            when (valor) {
                SwipeToDismissBoxValue.StartToEnd -> alCopiarUsuario()
                SwipeToDismissBoxValue.EndToStart -> alCopiarContrasena()
                else -> Unit
            }
            false
        }
    )
    SwipeToDismissBox(
        state = estadoSwipe,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Borde)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Copiar usuario", color = Menta, style = MaterialTheme.typography.bodyMedium)
                Text("Copiar contraseña", color = Ambar, style = MaterialTheme.typography.bodyMedium)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Superficie)
                .clickable { alAbrir() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Monograma(
                titulo = entrada.titulo.ifBlank { "?" },
                semilla = entrada.urls.firstOrNull() ?: entrada.passkey?.rpId ?: entrada.titulo
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entrada.titulo.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextoPrincipal,
                    maxLines = 1
                )
                Text(
                    when (entrada.tipo) {
                        TipoEntrada.PASSKEY -> "Passkey · ${entrada.passkey?.rpId ?: ""}"
                        TipoEntrada.NOTA -> "Nota segura"
                        TipoEntrada.LOGIN -> entrada.usuario.ifBlank { entrada.urls.firstOrNull() ?: "Sin usuario" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSecundario,
                    maxLines = 1
                )
            }
            IconButton(onClick = alFavorito, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorito",
                    tint = if (entrada.favorito) Ambar else Borde
                )
            }
        }
    }
}

