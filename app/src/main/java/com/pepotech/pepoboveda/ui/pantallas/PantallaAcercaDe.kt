package com.pepotech.pepoboveda.ui.pantallas

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pepotech.pepoboveda.crypto.VaultCrypto
import com.pepotech.pepoboveda.crypto.Wordlist
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.AjustesSistema
import com.pepotech.pepoboveda.util.Diagnostico
import com.pepotech.pepoboveda.util.InformeDiagnostico
import com.pepotech.pepoboveda.util.Portapapeles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaAcercaDe(vm: VaultViewModel) {
    val contexto = LocalContext.current
    val permisos = remember {
        try {
            val info = contexto.packageManager.getPackageInfo(contexto.packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    val tieneInternet = permisos.any { it.contains("INTERNET") }
    val tamanoArchivo = remember { vm.repositorio.archivoBoveda.length() }

    // Estado en vivo y últimos pasos: se refrescan cada vez que se vuelve a esta pantalla.
    // El sondeo habla con los servicios de biometría y cámara, así que va fuera del hilo principal.
    var estadoDispositivo by remember { mutableStateOf<List<String>>(emptyList()) }
    var registro by remember { mutableStateOf<List<String>>(emptyList()) }
    var refresco by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresco++
        onPauseOrDispose { }
    }
    LaunchedEffect(refresco) {
        registro = Diagnostico.ultimas(30)
        estadoDispositivo = withContext(Dispatchers.IO) { InformeDiagnostico.estado(contexto, vm.repositorio) }
    }

    fun informe(): String = InformeDiagnostico.generar(contexto, estadoDispositivo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Audítame", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            "No te pido confianza: te pido que lo compruebes.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion("Permisos declarados (leídos del sistema ahora mismo)")
            Spacer(Modifier.height(8.dp))
            if (permisos.isEmpty()) {
                Text("Ninguno", color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
            } else {
                permisos.forEach { permiso ->
                    Text("• ${permiso.substringAfterLast('.')}", color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (tieneInternet) "Atención: hay permiso de INTERNET declarado." else "Sin permiso de INTERNET. Esta app no puede abrir un socket.",
                color = if (tieneInternet) MaterialTheme.colorScheme.error else Menta,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion("Criptografía")
            Spacer(Modifier.height(8.dp))
            Texto("Derivación: Argon2id, 64 MiB de memoria, 3 iteraciones, paralelismo 4, salida de 32 bytes.")
            Texto("Cifrado: AES-256-GCM con nonce de 12 bytes y etiqueta de 128 bits.")
            Texto("La cabecera del archivo se autentica como AAD, así que nadie puede tocar los parámetros sin romper el descifrado.")
            Texto("La clave maestra vive solo en memoria mientras la bóveda está abierta y se sobrescribe al bloquear.")
            Texto("Huella, modo fuerte: la clave maestra se envuelve con una clave AES del Android Keystore que exige huella de Clase 3 en cada uso y se invalida si cambia la biometría.")
            Texto("Huella, modo compatible (solo si lo activas): la clave AES vive en el Keystore y no sale del móvil, pero no exige autenticación del chip; la huella o el PIN los comprueba Android y la app decide abrir. Es más débil y por eso se avisa.")
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion("Formato del archivo")
            Spacer(Modifier.height(8.dp))
            Texto("Magic \"BVDA\" · versión ${VaultCrypto.VERSION} · salt de ${VaultCrypto.TAM_SALT} bytes · parámetros Argon2 · nonce de ${VaultCrypto.TAM_NONCE} bytes · cuerpo AES-GCM.")
            Texto("Cabecera total: ${VaultCrypto.TAM_CABECERA} bytes.")
            Texto("Ruta: ${vm.repositorio.archivoBoveda.absolutePath}")
            Texto("Tamaño actual: $tamanoArchivo bytes.")
            Texto("Cada guardado escribe en un .tmp, hace fsync y renombra: si se corta la luz, no pierdes la bóveda.")
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion("Generador")
            Spacer(Modifier.height(8.dp))
            Texto("SecureRandom del sistema, sin semillas propias.")
            Texto("Diccionario español embebido: ${Wordlist.TAMANO} palabras.")
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion("Passkeys y autofill")
            Spacer(Modifier.height(8.dp))
            Texto("Las claves privadas son P-256 (ES256) generadas en el dispositivo y guardadas dentro del JSON cifrado.")
            Texto("El relleno automático solo ofrece entradas cuyo dominio raíz coincide con el de la app o web que lo pide.")
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion("Diagnóstico de este móvil")
            Spacer(Modifier.height(8.dp))
            Text(
                "Lo que Android dice de la huella y de la cámara, y los últimos pasos que dio la app con ellas. Aquí no entra nada tuyo: ni claves, ni contraseñas, ni el contenido de ningún QR. Si algo te falla, cópialo y mándamelo.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            if (estadoDispositivo.isEmpty()) {
                Text("Consultando al sistema…", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            }
            estadoDispositivo.forEach { linea ->
                Text(linea, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text("Últimos pasos", color = TextoSecundario, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Obsidiana)
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                if (registro.isEmpty()) {
                    Text("(nada registrado todavía)", color = TextoSecundario, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    registro.forEach { linea ->
                        Text(linea, color = TextoPrincipal, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BotonBorde("Copiar informe") {
                Portapapeles.copiar(contexto, "Diagnóstico Pepo Bóveda", informe())
                vm.avisar("Informe copiado. Pégalo donde me lo quieras mandar.")
            }
            Spacer(Modifier.height(10.dp))
            BotonBorde("Compartir informe") {
                // Compartir no devuelve resultado, así que no se exime al auto-bloqueo: si el
                // ajuste es "Al cerrar la app", al volver toca abrirla otra vez. Es lo correcto.
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Pepo Bóveda")
                    .putExtra(Intent.EXTRA_TEXT, informe())
                if (!AjustesSistema.abrir(contexto, Intent.createChooser(intent, "Compartir informe"))) {
                    vm.avisar("No hay ninguna app con la que compartirlo. Usa Copiar informe.")
                }
            }
            Spacer(Modifier.height(10.dp))
            BotonBorde("Borrar registro") {
                Diagnostico.borrar()
                registro = emptyList()
                vm.avisar("Registro borrado")
            }
        }

        Spacer(Modifier.height(20.dp))
        BotonBorde("Ver la ficha del sistema") {
            if (!AjustesSistema.abrirFichaApp(contexto)) vm.avisar("No se pudo abrir la ficha del sistema")
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde("Volver") { vm.volverALista() }
        Spacer(Modifier.height(24.dp))
        Text(
            "Hecho para PepoTech · sin internet, sin cuentas, sin excusas",
            color = Ambar,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Texto(texto: String) {
    Text(
        text = "• $texto",
        color = TextoPrincipal,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
