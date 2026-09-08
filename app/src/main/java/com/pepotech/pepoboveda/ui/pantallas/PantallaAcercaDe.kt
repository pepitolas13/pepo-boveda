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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pepotech.pepoboveda.R
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
        Text(stringResource(R.string.titulo_auditar), style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            stringResource(R.string.auditoria_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.label_permisos_sis))
            Spacer(Modifier.height(8.dp))
            if (permisos.isEmpty()) {
                Text(stringResource(R.string.permisos_ninguno), color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
            } else {
                permisos.forEach { permiso ->
                    Text("• ${permiso.substringAfterLast('.')}", color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (tieneInternet) stringResource(R.string.aviso_internet_si) else stringResource(R.string.aviso_internet_no),
                color = if (tieneInternet) MaterialTheme.colorScheme.error else Menta,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_cripto))
            Spacer(Modifier.height(8.dp))
            TextoItem(stringResource(R.string.cripto_derivacion))
            TextoItem(stringResource(R.string.cripto_cifrado))
            TextoItem(stringResource(R.string.cripto_aad))
            TextoItem(stringResource(R.string.cripto_memoria))
            TextoItem(stringResource(R.string.cripto_huella_fuerte))
            TextoItem(stringResource(R.string.cripto_huella_compatible))
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_formato))
            Spacer(Modifier.height(8.dp))
            TextoItem(stringResource(R.string.formato_magic, VaultCrypto.VERSION, VaultCrypto.TAM_SALT, VaultCrypto.TAM_NONCE))
            TextoItem(stringResource(R.string.formato_cabecera, VaultCrypto.TAM_CABECERA))
            TextoItem(stringResource(R.string.formato_ruta, vm.repositorio.archivoBoveda.absolutePath))
            TextoItem(stringResource(R.string.formato_tamano, tamanoArchivo))
            TextoItem(stringResource(R.string.formato_fsync))
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_generador_info))
            Spacer(Modifier.height(8.dp))
            TextoItem(stringResource(R.string.generador_securerandom))
            TextoItem(stringResource(R.string.generador_diccionario, Wordlist.TAMANO))
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_passkeys_info))
            Spacer(Modifier.height(8.dp))
            TextoItem(stringResource(R.string.passkeys_p256))
            TextoItem(stringResource(R.string.passkeys_autofill))
        }

        Spacer(Modifier.height(14.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_diagnostico))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.diagnostico_ayuda),
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            if (estadoDispositivo.isEmpty()) {
                Text(stringResource(R.string.diagnostico_consultando), color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            }
            estadoDispositivo.forEach { linea ->
                Text(linea, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.diagnostico_pasos), color = TextoSecundario, style = MaterialTheme.typography.labelLarge)
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
                    Text(stringResource(R.string.diagnostico_vacio), color = TextoSecundario, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    registro.forEach { linea ->
                        Text(linea, color = TextoPrincipal, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BotonBorde(stringResource(R.string.accion_copiar_informe)) {
                Portapapeles.copiar(contexto, "Diagnóstico Pepo Bóveda", informe())
                vm.avisar(contexto.getString(R.string.aviso_diagnostico_copiado))
            }
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_compartir_informe)) {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Pepo Bóveda")
                    .putExtra(Intent.EXTRA_TEXT, informe())
                if (!AjustesSistema.abrir(contexto, Intent.createChooser(intent, "Compartir informe"))) {
                    vm.avisar(contexto.getString(R.string.error_no_app_compartir))
                }
            }
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_borrar_registro)) {
                Diagnostico.borrar()
                registro = emptyList()
                vm.avisar(contexto.getString(R.string.aviso_eliminado))
            }
        }

        Spacer(Modifier.height(20.dp))
        BotonBorde(stringResource(R.string.accion_ver_ficha)) {
            if (!AjustesSistema.abrirFichaApp(contexto)) vm.avisar(contexto.getString(R.string.error_pantalla_no_encontrada))
        }
        Spacer(Modifier.height(12.dp))
        BotonBorde(stringResource(R.string.accion_volver)) { vm.volverALista() }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.pie_acerca_de),
            color = Ambar,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TextoItem(texto: String) {
    Text(
        text = "• $texto",
        color = TextoPrincipal,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
