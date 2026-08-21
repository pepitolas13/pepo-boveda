package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.crypto.Totp
import com.pepotech.pepoboveda.data.EstadoBoveda
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.AnilloTotp
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import kotlinx.coroutines.delay

@Composable
fun PantallaAutenticador(vm: VaultViewModel, estado: EstadoBoveda) {
    val entradas = (estado as? EstadoBoveda.Desbloqueada)?.entradas ?: emptyList()
    val conTotp = vm.entradasConTotp(entradas)

    var ahora by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            ahora = System.currentTimeMillis() / 1000
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Autenticador", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            "Códigos de doble factor calculados aquí, en tu móvil, con el reloj y una clave que nunca sale de la bóveda. Sin cuenta, sin nube, sin permiso de red.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(18.dp))

        BotonAmbar("Escanear QR con la cámara") { vm.ir(Pantalla.Escaner()) }
        Spacer(Modifier.height(10.dp))
        BotonBorde("Escribir el código a mano") { vm.ir(Pantalla.Escaner(soloManual = true)) }
        Spacer(Modifier.height(18.dp))

        if (conTotp.isEmpty()) {
            TarjetaPepo {
                Text("Todavía no hay dobles factores", color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cuando una web te ofrezca activar el 2FA, escanea su QR o pega su clave. Aquí verás el código de 6 dígitos.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            conTotp.forEach { entrada ->
                val secreto = entrada.secretoTotp ?: return@forEach
                val periodo = entrada.totpPeriodo.toLong().coerceAtLeast(10L)
                val codigo = remember(ahora / periodo, secreto, entrada.totpDigitos) {
                    try {
                        Totp.codigo(
                            secreto = com.pepotech.pepoboveda.crypto.Base32.decodificar(secreto),
                            segundosUnix = ahora,
                            digitos = entrada.totpDigitos,
                            periodo = periodo
                        )
                    } catch (e: Exception) {
                        "------"
                    }
                }
                TarjetaPepo(alPulsar = { vm.copiar("Código 2FA", codigo, true) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnilloTotp(
                            codigo = codigo,
                            segundosRestantes = Totp.segundosRestantes(ahora, periodo),
                            tamano = 80
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entrada.totpEmisor.ifBlank { entrada.titulo },
                                color = TextoPrincipal,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (entrada.usuario.isNotBlank()) {
                                Text(entrada.usuario, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Toca para copiar", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonBorde("Volver", modifier = Modifier.weight(1f)) { vm.volverALista() }
        }
        Spacer(Modifier.height(40.dp))
    }
}
