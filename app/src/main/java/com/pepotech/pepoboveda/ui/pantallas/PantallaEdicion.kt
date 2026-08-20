package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.crypto.Base32
import com.pepotech.pepoboveda.crypto.OpcionesGenerador
import com.pepotech.pepoboveda.crypto.PasswordGenerator
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.TipoEntrada
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BarraFuerza
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.DegradadoAmbar
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica
import com.pepotech.pepoboveda.util.MedidorFuerza

@Composable
fun PantallaEdicion(vm: VaultViewModel, id: String?, contrasenaInicial: String) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    val original = remember(id) { id?.let { vm.entrada(it) } }

    var tipo by remember { mutableStateOf(original?.tipo ?: TipoEntrada.LOGIN) }
    var titulo by remember { mutableStateOf(original?.titulo ?: "") }
    var usuario by remember { mutableStateOf(original?.usuario ?: "") }
    var contrasena by remember { mutableStateOf(original?.contrasena ?: contrasenaInicial) }
    var mostrarContrasena by remember { mutableStateOf(false) }
    var urls by remember { mutableStateOf(original?.urls?.joinToString(", ") ?: "") }
    var notas by remember { mutableStateOf(original?.notas ?: "") }
    var totp by remember { mutableStateOf(original?.secretoTotp ?: "") }
    var favorito by remember { mutableStateOf(original?.favorito ?: false) }

    val totpValido = totp.isBlank() || Base32.esValido(totp)
    val fuerza = remember(contrasena) { MedidorFuerza.medir(contrasena) }
    val puedeGuardar = titulo.isNotBlank() && totpValido

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            if (original == null) "Nueva entrada" else "Editar entrada",
            style = MaterialTheme.typography.headlineMedium,
            color = TextoPrincipal
        )
        Spacer(Modifier.height(16.dp))

        if (original?.passkey == null) {
            EtiquetaSeccion("Tipo")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorTipo("Contraseña", tipo == TipoEntrada.LOGIN) { tipo = TipoEntrada.LOGIN }
                SelectorTipo("Nota segura", tipo == TipoEntrada.NOTA) { tipo = TipoEntrada.NOTA }
            }
            Spacer(Modifier.height(16.dp))
        }

        CampoPepo(valor = titulo, etiqueta = "Título", alCambiar = { titulo = it })
        Spacer(Modifier.height(12.dp))

        if (tipo != TipoEntrada.NOTA) {
            CampoPepo(valor = usuario, etiqueta = "Usuario o correo", alCambiar = { usuario = it })
            Spacer(Modifier.height(12.dp))
            CampoPepo(
                valor = contrasena,
                etiqueta = "Contraseña",
                alCambiar = { contrasena = it },
                esContrasena = true,
                mostrarContrasena = mostrarContrasena,
                monoespaciada = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (mostrarContrasena) "Ocultar" else "Mostrar",
                    color = Ambar,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { mostrarContrasena = !mostrarContrasena }
                )
                Text(
                    "Generar ahora",
                    color = Ambar,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable {
                        haptica.toque()
                        contrasena = PasswordGenerator.generar(OpcionesGenerador())
                    }
                )
                Text(
                    "Abrir generador",
                    color = Ambar,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { vm.ir(Pantalla.Generador) }
                )
            }
            if (contrasena.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BarraFuerza(fuerza.fraccion, fuerza.etiqueta, fuerza.tiempo)
            }
            Spacer(Modifier.height(12.dp))
            CampoPepo(
                valor = urls,
                etiqueta = "Sitios o paquetes (separados por comas)",
                alCambiar = { urls = it }
            )
            Spacer(Modifier.height(12.dp))
            CampoPepo(
                valor = totp,
                etiqueta = "Secreto TOTP en Base32 (opcional)",
                alCambiar = { totp = it.uppercase() },
                monoespaciada = true
            )
            if (!totpValido) {
                Spacer(Modifier.height(6.dp))
                Text("Ese secreto no es Base32 válido", color = Peligro, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

        CampoPepo(valor = notas, etiqueta = "Notas", alCambiar = { notas = it }, varias = true)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Superficie)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Favorito", color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                Text("Aparece arriba en la lista", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = favorito,
                onCheckedChange = { favorito = it; haptica.tic() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Obsidiana,
                    checkedTrackColor = Ambar,
                    uncheckedTrackColor = Borde
                )
            )
        }

        Spacer(Modifier.height(20.dp))
        BotonAmbar(if (original == null) "Guardar en la bóveda" else "Guardar cambios", activo = puedeGuardar) {
            haptica.exito()
            val entrada = Entrada(
                id = original?.id ?: vm.nuevoId(),
                tipo = original?.passkey?.let { TipoEntrada.PASSKEY } ?: tipo,
                titulo = titulo.trim(),
                usuario = usuario.trim(),
                contrasena = contrasena,
                urls = urls.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                notas = notas,
                secretoTotp = totp.trim().ifBlank { null },
                favorito = favorito,
                creadaEn = original?.creadaEn ?: 0L,
                passkey = original?.passkey
            )
            vm.guardar(entrada)
            if (original == null) vm.volverALista() else vm.ir(Pantalla.Detalle(entrada.id))
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Cancelar") {
            if (original == null) vm.volverALista() else vm.ir(Pantalla.Detalle(original.id))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SelectorTipo(texto: String, activo: Boolean, alPulsar: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(forma)
            .background(if (activo) DegradadoAmbar else Brush.horizontalGradient(listOf(Superficie, Superficie)))
            .clickable { alPulsar() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(texto, color = if (activo) Obsidiana else TextoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}
