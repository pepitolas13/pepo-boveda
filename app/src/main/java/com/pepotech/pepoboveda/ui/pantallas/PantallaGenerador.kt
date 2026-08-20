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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.crypto.OpcionesGenerador
import com.pepotech.pepoboveda.crypto.PasswordGenerator
import com.pepotech.pepoboveda.crypto.Wordlist
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.ContrasenaSlotMachine
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.DegradadoAmbar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica
import kotlin.math.roundToInt

@Composable
fun PantallaGenerador(vm: VaultViewModel) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }

    var opciones by remember { mutableStateOf(OpcionesGenerador()) }
    var generada by remember { mutableStateOf("") }
    var generacion by remember { mutableIntStateOf(0) }

    fun regenerar() {
        generada = PasswordGenerator.generar(opciones)
        generacion++
    }

    LaunchedEffect(opciones) { regenerar() }

    val bits = PasswordGenerator.entropiaBits(opciones)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Generador", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            "Aleatoriedad de SecureRandom, aquí en el móvil.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion("Resultado")
            Spacer(Modifier.height(12.dp))
            ContrasenaSlotMachine(objetivo = generada, generacion = generacion, haptica = haptica)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("${bits.roundToInt()} bits", color = Menta, style = MaterialTheme.typography.labelLarge)
                Text(
                    "resistiría ${PasswordGenerator.tiempoDeCrackeo(bits)}",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModoChip("Aleatoria", !opciones.modoFrase) { opciones = opciones.copy(modoFrase = false) }
            ModoChip("Frase memorizable", opciones.modoFrase) { opciones = opciones.copy(modoFrase = true) }
        }

        Spacer(Modifier.height(16.dp))

        if (opciones.modoFrase) {
            TarjetaPepo {
                EtiquetaSeccion("Palabras: ${opciones.palabras}")
                Slider(
                    value = opciones.palabras.toFloat(),
                    onValueChange = {
                        val nuevo = it.roundToInt().coerceIn(4, 8)
                        if (nuevo != opciones.palabras) {
                            haptica.tic()
                            opciones = opciones.copy(palabras = nuevo)
                        }
                    },
                    valueRange = 4f..8f,
                    steps = 3,
                    colors = coloresSlider()
                )
                Text(
                    "Diccionario español de ${Wordlist.TAMANO} palabras, todo dentro del APK.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            TarjetaPepo {
                EtiquetaSeccion("Longitud: ${opciones.longitud}")
                Slider(
                    value = opciones.longitud.toFloat(),
                    onValueChange = {
                        val nuevo = it.roundToInt().coerceIn(8, 64)
                        if (nuevo != opciones.longitud) {
                            haptica.tic()
                            opciones = opciones.copy(longitud = nuevo)
                        }
                    },
                    valueRange = 8f..64f,
                    colors = coloresSlider()
                )
                FilaInterruptor("Mayúsculas", opciones.mayusculas) { opciones = opciones.copy(mayusculas = it) }
                FilaInterruptor("Minúsculas", opciones.minusculas) { opciones = opciones.copy(minusculas = it) }
                FilaInterruptor("Dígitos", opciones.digitos) { opciones = opciones.copy(digitos = it) }
                FilaInterruptor("Símbolos", opciones.simbolos) { opciones = opciones.copy(simbolos = it) }
            }
        }

        Spacer(Modifier.height(20.dp))
        BotonAmbar("Generar otra") {
            haptica.toque()
            regenerar()
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Copiar") {
            haptica.exito()
            vm.copiar("Contraseña", generada, sensible = true)
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Usar en una entrada nueva") {
            vm.ir(Pantalla.Editar(null, generada))
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Volver") { vm.volverALista() }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun coloresSlider() = SliderDefaults.colors(
    thumbColor = Ambar,
    activeTrackColor = Ambar,
    inactiveTrackColor = Borde
)

@Composable
private fun FilaInterruptor(texto: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, color = TextoPrincipal, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = activo,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidiana,
                checkedTrackColor = Ambar,
                uncheckedTrackColor = Borde
            )
        )
    }
}

@Composable
private fun ModoChip(texto: String, activo: Boolean, alPulsar: () -> Unit) {
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
