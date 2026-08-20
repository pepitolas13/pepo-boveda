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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.Monograma
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.AjustesSistema
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaPasskeys(vm: VaultViewModel) {
    val contexto = LocalContext.current
    val passkeys = remember(vm.repositorio.entradas()) { vm.repositorio.passkeys() }
    val formato = remember { SimpleDateFormat("d MMM yyyy", Locale("es", "ES")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Passkeys", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            "Una passkey es un par de claves: la pública se la queda la web, la privada se queda aquí. No hay contraseña que robar en un phishing.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion("Cómo activarlas")
            Spacer(Modifier.height(8.dp))
            Text(
                "Android tiene que saber que Pepo Bóveda es tu gestor. El botón te deja en la pantalla de \"Contraseñas y llaves de acceso\": ahí marca Pepo Bóveda.",
                color = TextoPrincipal,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            BotonBorde("Abrir contraseñas y llaves de acceso") {
                if (!AjustesSistema.abrirProveedorCredenciales(contexto)) {
                    vm.avisar("Tu móvil no deja abrirla directa: Ajustes › Contraseñas y cuentas › Contraseñas y llaves de acceso")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Cada fabricante la coloca en un sitio distinto. Si el botón te deja en un menú de ajustes, busca \"Contraseñas\" en su buscador.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))

        if (passkeys.isEmpty()) {
            TarjetaPepo {
                Text("Todavía no hay passkeys", color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cuando una web o app te pida crear una passkey y elijas Pepo Bóveda, aparecerá en esta lista.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            passkeys.forEach { entrada ->
                val datos = entrada.passkey ?: return@forEach
                TarjetaPepo(alPulsar = { vm.ir(Pantalla.Detalle(entrada.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monograma(titulo = datos.rpName.ifBlank { datos.rpId }, semilla = datos.rpId, tamano = 42)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(datos.rpName.ifBlank { datos.rpId }, color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                            Text(
                                datos.usuario.ifBlank { entrada.usuario.ifBlank { datos.rpId } },
                                color = TextoSecundario,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Creada el ${formato.format(Date(entrada.creadaEn))}",
                                color = Menta,
                                style = MaterialTheme.typography.bodyMedium
                            )
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
