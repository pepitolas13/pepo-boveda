package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BarraFuerza
import com.pepotech.pepoboveda.ui.componentes.BarraProgresoForja
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.PuertaBoveda
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica
import com.pepotech.pepoboveda.util.MedidorFuerza

@Composable
fun PantallaOnboarding(vm: VaultViewModel, actividad: FragmentActivity) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    var paso by remember { mutableIntStateOf(0) }
    var contrasena by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var mostrar by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = paso,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.94f) + fadeIn(spring(dampingRatio = 0.6f))) togetherWith
                fadeOut(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
        },
        label = "onboarding"
    ) { actual ->
        when (actual) {
            0 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PuertaBoveda(abierta = false, tamano = 190)
                Spacer(Modifier.height(28.dp))
                Text(
                    "Pepo Bóveda",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextoPrincipal
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Esta app no tiene permiso de internet — compruébalo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ambar,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                TarjetaPepo {
                    Text("Lo que hay dentro del manifest", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
                    Spacer(Modifier.height(10.dp))
                    Text("• USE_BIOMETRIC — para abrir con tu huella", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                    Text("• VIBRATE — para las microinteracciones", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                    Text("• CAMERA — solo para leer el QR de un 2FA, y solo si tú pulsas escanear", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("Y nada más. Sin red, sin analítica, sin copias en la nube.", color = Menta, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(28.dp))
                BotonAmbar("Crear mi bóveda") {
                    haptica.toque()
                    paso = 1
                }
            }

            1 -> {
                val fuerza = remember(contrasena) { MedidorFuerza.medir(contrasena) }
                val coinciden = contrasena.isNotEmpty() && contrasena == repetida
                val valida = contrasena.length >= 10 && coinciden
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Tu contraseña maestra", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Es la única llave. No se guarda en ningún sitio y no hay recuperación: si la pierdes, la bóveda se queda cerrada para siempre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario
                    )
                    Spacer(Modifier.height(24.dp))
                    CampoPepo(
                        valor = contrasena,
                        etiqueta = "Contraseña maestra",
                        alCambiar = { contrasena = it },
                        esContrasena = true,
                        mostrarContrasena = mostrar,
                        monoespaciada = true
                    )
                    Spacer(Modifier.height(12.dp))
                    CampoPepo(
                        valor = repetida,
                        etiqueta = "Repítela",
                        alCambiar = { repetida = it },
                        esContrasena = true,
                        mostrarContrasena = mostrar,
                        monoespaciada = true
                    )
                    Spacer(Modifier.height(16.dp))
                    BarraFuerza(fuerza.fraccion, fuerza.etiqueta, fuerza.tiempo)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (repetida.isEmpty()) "Mínimo 10 caracteres" else if (coinciden) "Las dos coinciden" else "No coinciden",
                            color = if (repetida.isNotEmpty() && !coinciden) MaterialTheme.colorScheme.error else TextoSecundario,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (mostrar) "Ocultar" else "Mostrar",
                            color = Ambar,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 12.dp)
                                .clickable { mostrar = !mostrar }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    BotonAmbar("Forjar la bóveda", activo = valida) {
                        haptica.toque()
                        paso = 2
                    }
                    Spacer(Modifier.height(12.dp))
                    BotonBorde("Volver") { paso = 0 }
                }
            }

            else -> {
                LaunchedEffect(Unit) {
                    vm.crearBoveda(contrasena) { haptica.exito() }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PuertaBoveda(abierta = false, tamano = 210)
                    Spacer(Modifier.height(32.dp))
                    Text("Forjando tu bóveda", style = MaterialTheme.typography.headlineSmall, color = TextoPrincipal)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Argon2id está estirando tu contraseña con 64 MiB de memoria y 3 pasadas. Esto es lo que hace que un ataque por fuerza bruta salga carísimo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    BarraProgresoForja()
                }
            }
        }
    }
}
