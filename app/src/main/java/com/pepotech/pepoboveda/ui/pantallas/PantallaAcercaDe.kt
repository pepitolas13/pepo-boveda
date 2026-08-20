package com.pepotech.pepoboveda.ui.pantallas

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pepotech.pepoboveda.crypto.VaultCrypto
import com.pepotech.pepoboveda.crypto.Wordlist
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario

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
            Texto("Biometría: la clave maestra se envuelve con una clave AES del Android Keystore que exige Clase 3 en cada uso.")
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

        Spacer(Modifier.height(20.dp))
        BotonBorde("Ver la ficha del sistema") {
            try {
                contexto.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                vm.avisar("No se pudo abrir la ficha del sistema")
            }
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
